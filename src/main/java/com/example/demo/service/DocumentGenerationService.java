package com.example.demo.service;

import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.dto.GenerateDocumentRequest;
import com.example.demo.exception.BadRequestException;
import com.example.demo.model.GeneratedDocumentMetadata;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.service.engine.GeneratedFile;
import com.example.demo.service.engine.TemplateEngine;

@Service
public class DocumentGenerationService {

    private static final Pattern DATA_URI_BASE64_PATTERN =
            Pattern.compile("^data:[^;]+;base64,(.+)$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_SIGNATURE_BYTES = 2_000_000;

    private final FileStorageService fileStorageService;
    private final TemplateRequirementsService templateRequirementsService;
    private final List<TemplateEngine> templateEngines;

    public DocumentGenerationService(
            FileStorageService fileStorageService,
            TemplateRequirementsService templateRequirementsService,
            List<TemplateEngine> templateEngines) {
        this.fileStorageService = fileStorageService;
        this.templateRequirementsService = templateRequirementsService;
        this.templateEngines = templateEngines;
    }

    public GeneratedDocumentMetadata generate(GenerateDocumentRequest request) {
        TemplateMetadata template = fileStorageService.loadTemplate(request.getTemplateId());
        TemplateEngine engine = templateEngines.stream()
                .filter(it -> it.supports(template.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe motor para tipo " + template.type()));

        validateRequiredSignatures(template, request.signaturesOrEmpty().keySet());
        Map<String, byte[]> signatures = decodeSignatures(request.signaturesOrEmpty());

        GeneratedFile generatedFile = engine.generate(
                Path.of(template.filePath()),
                request.fieldsOrEmpty(),
                signatures);

        return fileStorageService.storeGeneratedDocument(
                template.id(),
                generatedFile.type(),
                generatedFile.content());
    }

    private void validateRequiredSignatures(TemplateMetadata template, Set<String> providedSignatureKeys) {
        Set<String> required = templateRequirementsService.extractRequiredSignatures(template);
        if (required.isEmpty()) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(providedSignatureKeys);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Faltan firmas requeridas: " + String.join(", ", missing));
        }
    }

    private Map<String, byte[]> decodeSignatures(Map<String, String> signatures) {
        return signatures.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> decodeBase64Signature(entry.getValue())));
    }

    private byte[] decodeBase64Signature(String rawBase64) {
        if (rawBase64 == null || rawBase64.isBlank()) {
            throw new BadRequestException("Firma base64 vacia");
        }
        String cleaned = rawBase64.trim();
        Matcher matcher = DATA_URI_BASE64_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            cleaned = matcher.group(1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(cleaned);
            if (bytes.length > MAX_SIGNATURE_BYTES) {
                throw new BadRequestException("La firma supera el tamano maximo permitido");
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Firma con formato base64 invalido");
        }
    }
}
