package com.example.demo.signing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.GenerateDocumentRequest;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.GeneratedDocumentMetadata;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.service.DocumentGenerationService;
import com.example.demo.service.FileStorageService;
import com.example.demo.signing.dto.CreateDraftRequest;
import com.example.demo.signing.dto.DraftSignerInput;
import com.example.demo.signing.dto.DraftSignerView;
import com.example.demo.signing.dto.DraftStatusResponse;
import com.example.demo.signing.dto.FinalizeDraftResponse;
import com.example.demo.signing.dto.SignByTokenInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class DraftService {

    private static final String ACTION_DRAFT_CREATED = "DRAFT_CREATED";
    private static final String ACTION_SLOT_VIEWED = "SLOT_VIEWED";
    private static final String ACTION_SLOT_SIGNED = "SLOT_SIGNED";
    private static final String ACTION_DRAFT_FINALIZED = "DRAFT_FINALIZED";
    private static final String ACTION_DRAFT_CANCELLED = "DRAFT_CANCELLED";

    private final DraftRepository draftRepository;
    private final SignerSlotRepository slotRepository;
    private final FileStorageService fileStorageService;
    private final DocumentGenerationService documentGenerationService;
    private final AuditService auditService;
    private final DraftProperties draftProperties;
    private final ObjectMapper objectMapper;

    public DraftService(
            DraftRepository draftRepository,
            SignerSlotRepository slotRepository,
            FileStorageService fileStorageService,
            DocumentGenerationService documentGenerationService,
            AuditService auditService,
            DraftProperties draftProperties,
            ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.slotRepository = slotRepository;
        this.fileStorageService = fileStorageService;
        this.documentGenerationService = documentGenerationService;
        this.auditService = auditService;
        this.draftProperties = draftProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DraftStatusResponse createDraft(CreateDraftRequest request, HttpServletRequest httpRequest) {
        TemplateMetadata template = fileStorageService.loadTemplate(request.getTemplateId());

        List<DraftSignerInput> signers = request.getSigners();
        if (signers == null || signers.isEmpty()) {
            throw new BadRequestException("Debes incluir al menos un firmante");
        }

        String outputType = (request.getOutputType() == null || request.getOutputType().isBlank())
                ? template.type().extension()
                : request.getOutputType().trim().toLowerCase(java.util.Locale.ROOT);

        Draft draft = new Draft();
        draft.setId(UUID.randomUUID().toString());
        draft.setTemplateId(template.id());
        draft.setTitle(request.getTitle());
        draft.setStatus(DraftStatus.PENDIENTE);
        draft.setCreatedAt(Instant.now());
        draft.setOutputType(outputType);
        draft.setFieldsJson(writeJson(request.getFields()));
        draft.setDataJson(request.getDataJson());

        Instant tokenExpires = draft.getCreatedAt().plus(draftProperties.getTokenTtlDays(), ChronoUnit.DAYS);
        for (int i = 0; i < signers.size(); i++) {
            DraftSignerInput input = signers.get(i);
            SignerSlot slot = new SignerSlot();
            slot.setId(UUID.randomUUID().toString());
            slot.setDraft(draft);
            slot.setRowIndex(i + 1);
            slot.setSignerName(input.getSignerName());
            slot.setSignerEmail(input.getSignerEmail());
            slot.setSignerNif(input.getSignerNif());
            String key = (input.getSignatureKey() == null || input.getSignatureKey().isBlank())
                    ? "FIRMA_" + (i + 1)
                    : input.getSignatureKey().trim();
            slot.setSignatureKey(key);
            slot.setToken(generateToken());
            slot.setTokenExpiresAt(tokenExpires);
            slot.setStatus(SignerSlotStatus.PENDIENTE);
            draft.getSigners().add(slot);
        }

        Draft saved = draftRepository.save(draft);
        auditService.record(ACTION_DRAFT_CREATED, saved.getId(), null, httpRequest,
                "signers=" + saved.getSigners().size());
        return toStatusResponse(saved);
    }

    @Transactional(readOnly = true)
    public DraftStatusResponse getDraft(String draftId) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new NotFoundException("Borrador no encontrado: " + draftId));
        return toStatusResponse(draft);
    }

    @Transactional(readOnly = true)
    public List<DraftStatusResponse> listDrafts() {
        return draftRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toStatusResponse)
                .toList();
    }

    @Transactional
    public SignByTokenInfo viewByToken(String token, HttpServletRequest httpRequest) {
        SignerSlot slot = loadSlotByToken(token);
        auditService.record(ACTION_SLOT_VIEWED, slot.getDraft().getId(), slot.getId(), httpRequest, null);
        return toTokenInfo(slot);
    }

    @Transactional
    public SignByTokenInfo signByToken(String token, String signatureBase64, HttpServletRequest httpRequest) {
        SignerSlot slot = loadSlotByToken(token);

        if (slot.getStatus() == SignerSlotStatus.FIRMADO) {
            throw new BadRequestException("Esta fila ya fue firmada");
        }
        if (slot.getStatus() != SignerSlotStatus.PENDIENTE) {
            throw new BadRequestException("La fila no esta disponible para firmar");
        }
        if (slot.getTokenExpiresAt().isBefore(Instant.now())) {
            slot.setStatus(SignerSlotStatus.EXPIRADO);
            slotRepository.save(slot);
            throw new BadRequestException("El enlace ha expirado");
        }
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            throw new BadRequestException("Firma base64 vacia");
        }

        Draft draft = slot.getDraft();
        if (draft.getStatus() == DraftStatus.FINALIZADO || draft.getStatus() == DraftStatus.CANCELADO) {
            throw new BadRequestException("El borrador ya no acepta firmas");
        }

        String cleaned = signatureBase64.trim();
        slot.setSignatureBase64(cleaned);
        slot.setSignedAt(Instant.now());
        slot.setSignedFromIp(extractClientIp(httpRequest));
        String ua = httpRequest == null ? null : httpRequest.getHeader("User-Agent");
        slot.setSignedUserAgent(ua == null ? null : (ua.length() > 500 ? ua.substring(0, 500) : ua));
        slot.setStatus(SignerSlotStatus.FIRMADO);
        slot.setSignatureSha256(sha256Hex(cleaned));

        recalcDraftStatus(draft);
        slotRepository.save(slot);
        draftRepository.save(draft);

        auditService.record(ACTION_SLOT_SIGNED, draft.getId(), slot.getId(), httpRequest,
                "sha256=" + slot.getSignatureSha256());

        return toTokenInfo(slot);
    }

    @Transactional
    public FinalizeDraftResponse finalizeDraft(String draftId, boolean allowIncomplete, HttpServletRequest httpRequest) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new NotFoundException("Borrador no encontrado: " + draftId));

        if (draft.getStatus() == DraftStatus.FINALIZADO) {
            throw new BadRequestException("El borrador ya fue finalizado");
        }
        if (draft.getStatus() == DraftStatus.CANCELADO) {
            throw new BadRequestException("El borrador esta cancelado");
        }

        long pendientes = draft.getSigners().stream()
                .filter(s -> s.getStatus() != SignerSlotStatus.FIRMADO)
                .count();
        if (pendientes > 0 && !allowIncomplete) {
            throw new BadRequestException("Quedan " + pendientes + " firmas pendientes");
        }

        Map<String, String> fields = readFieldsJson(draft.getFieldsJson());
        Map<String, String> signaturesPayload = new LinkedHashMap<>();
        for (SignerSlot slot : draft.getSigners()) {
            if (slot.getStatus() == SignerSlotStatus.FIRMADO && slot.getSignatureBase64() != null) {
                signaturesPayload.put(slot.getSignatureKey(), slot.getSignatureBase64());
            }
        }

        GenerateDocumentRequest gdr = new GenerateDocumentRequest();
        gdr.setTemplateId(draft.getTemplateId());
        gdr.setFields(fields);
        gdr.setSignatures(signaturesPayload);
        gdr.setDataJson(draft.getDataJson());
        gdr.setOutputType(draft.getOutputType());

        GeneratedDocumentMetadata generated = documentGenerationService.generate(gdr);

        draft.setStatus(DraftStatus.FINALIZADO);
        draft.setFinalizedAt(Instant.now());
        draft.setGeneratedDocumentId(generated.id());
        draftRepository.save(draft);

        auditService.record(ACTION_DRAFT_FINALIZED, draft.getId(), null, httpRequest,
                "documentId=" + generated.id() + ";pendientes=" + pendientes);

        return new FinalizeDraftResponse(
                draft.getId(),
                draft.getStatus(),
                generated.id(),
                "/api/documents/" + generated.id(),
                draft.getOutputType(),
                draft.getFinalizedAt());
    }

    @Transactional
    public DraftStatusResponse cancelDraft(String draftId, HttpServletRequest httpRequest) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new NotFoundException("Borrador no encontrado: " + draftId));
        if (draft.getStatus() == DraftStatus.FINALIZADO) {
            throw new BadRequestException("No se puede cancelar un borrador finalizado");
        }
        draft.setStatus(DraftStatus.CANCELADO);
        for (SignerSlot slot : draft.getSigners()) {
            if (slot.getStatus() == SignerSlotStatus.PENDIENTE) {
                slot.setStatus(SignerSlotStatus.REVOCADO);
            }
        }
        Draft saved = draftRepository.save(draft);
        auditService.record(ACTION_DRAFT_CANCELLED, draftId, null, httpRequest, null);
        return toStatusResponse(saved);
    }

    private SignerSlot loadSlotByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Token vacio");
        }
        return slotRepository.findByToken(token.trim())
                .orElseThrow(() -> new NotFoundException("Enlace de firma no encontrado"));
    }

    private void recalcDraftStatus(Draft draft) {
        long total = draft.getSigners().size();
        long firmados = draft.getSigners().stream()
                .filter(s -> s.getStatus() == SignerSlotStatus.FIRMADO)
                .count();
        if (firmados == 0) {
            draft.setStatus(DraftStatus.PENDIENTE);
        } else if (firmados < total) {
            draft.setStatus(DraftStatus.PARCIAL);
        } else {
            draft.setStatus(DraftStatus.COMPLETO);
        }
    }

    private DraftStatusResponse toStatusResponse(Draft draft) {
        List<DraftSignerView> views = new ArrayList<>();
        for (SignerSlot slot : draft.getSigners()) {
            views.add(new DraftSignerView(
                    slot.getId(),
                    slot.getRowIndex(),
                    slot.getSignerName(),
                    slot.getSignerEmail(),
                    slot.getSignerNif(),
                    slot.getSignatureKey(),
                    slot.getStatus(),
                    slot.getSignedAt(),
                    slot.getTokenExpiresAt(),
                    buildShareUrl(slot.getToken())));
        }
        long firmados = draft.getSigners().stream()
                .filter(s -> s.getStatus() == SignerSlotStatus.FIRMADO)
                .count();
        return new DraftStatusResponse(
                draft.getId(),
                draft.getTemplateId(),
                draft.getTitle(),
                draft.getStatus(),
                draft.getOutputType(),
                draft.getSigners().size(),
                (int) firmados,
                draft.getCreatedAt(),
                draft.getFinalizedAt(),
                draft.getGeneratedDocumentId(),
                views);
    }

    private SignByTokenInfo toTokenInfo(SignerSlot slot) {
        Draft draft = slot.getDraft();
        return new SignByTokenInfo(
                draft.getId(),
                draft.getTitle(),
                draft.getStatus(),
                slot.getStatus(),
                slot.getRowIndex(),
                slot.getSignerName(),
                slot.getSignerNif(),
                slot.getSignatureKey(),
                slot.getTokenExpiresAt(),
                slot.getSignedAt());
    }

    private String buildShareUrl(String token) {
        String base = draftProperties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return "/firmar/" + token;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/firmar/" + token;
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(System.nanoTime());
    }

    private String writeJson(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("No se pudo serializar fields");
        }
    }

    private Map<String, String> readFieldsJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException ex) {
            return new HashMap<>();
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
