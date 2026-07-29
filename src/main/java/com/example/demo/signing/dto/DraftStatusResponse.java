package com.example.demo.signing.dto;

import java.time.Instant;
import java.util.List;

import com.example.demo.signing.DraftStatus;

public record DraftStatusResponse(
        String draftId,
        String templateId,
        String title,
        DraftStatus status,
        String outputType,
        int totalSigners,
        int signedSigners,
        Instant createdAt,
        Instant finalizedAt,
        String generatedDocumentId,
        List<DraftSignerView> signers) {
}
