package com.example.demo.signing.dto;

import java.time.Instant;

import com.example.demo.signing.DraftStatus;

public record FinalizeDraftResponse(
        String draftId,
        DraftStatus status,
        String generatedDocumentId,
        String downloadUrl,
        String outputType,
        Instant finalizedAt) {
}
