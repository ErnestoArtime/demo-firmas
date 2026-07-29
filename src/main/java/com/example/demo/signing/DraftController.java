package com.example.demo.signing;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.signing.dto.AuditLogEntry;
import com.example.demo.signing.dto.CreateDraftRequest;
import com.example.demo.signing.dto.DraftStatusResponse;
import com.example.demo.signing.dto.FinalizeDraftResponse;
import com.example.demo.signing.dto.SignByTokenInfo;
import com.example.demo.signing.dto.SignByTokenRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftService draftService;
    private final AuditService auditService;

    public DraftController(DraftService draftService, AuditService auditService) {
        this.draftService = draftService;
        this.auditService = auditService;
    }

    @PostMapping
    public DraftStatusResponse create(
            @Valid @RequestBody CreateDraftRequest request,
            HttpServletRequest httpRequest) {
        return draftService.createDraft(request, httpRequest);
    }

    @GetMapping
    public List<DraftStatusResponse> list() {
        return draftService.listDrafts();
    }

    @GetMapping("/{draftId}")
    public DraftStatusResponse get(@PathVariable String draftId) {
        return draftService.getDraft(draftId);
    }

    @GetMapping("/{draftId}/audit")
    public List<AuditLogEntry> audit(@PathVariable String draftId) {
        return auditService.listForDraft(draftId);
    }

    @PostMapping("/{draftId}/finalize")
    public FinalizeDraftResponse finalizeDraft(
            @PathVariable String draftId,
            @RequestParam(name = "allowIncomplete", defaultValue = "false") boolean allowIncomplete,
            HttpServletRequest httpRequest) {
        return draftService.finalizeDraft(draftId, allowIncomplete, httpRequest);
    }

    @DeleteMapping("/{draftId}")
    public DraftStatusResponse cancel(@PathVariable String draftId, HttpServletRequest httpRequest) {
        return draftService.cancelDraft(draftId, httpRequest);
    }

    @GetMapping("/sign/{token}")
    public SignByTokenInfo viewByToken(
            @PathVariable String token,
            HttpServletRequest httpRequest) {
        return draftService.viewByToken(token, httpRequest);
    }

    @PostMapping("/sign/{token}")
    public SignByTokenInfo signByToken(
            @PathVariable String token,
            @Valid @RequestBody SignByTokenRequest request,
            HttpServletRequest httpRequest) {
        return draftService.signByToken(token, request.getSignatureBase64(), httpRequest);
    }
}
