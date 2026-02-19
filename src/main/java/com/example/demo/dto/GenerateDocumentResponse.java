package com.example.demo.dto;

import java.time.Instant;

import com.example.demo.model.TemplateType;

public record GenerateDocumentResponse(
        String documentId,
        String templateId,
        TemplateType type,
        String downloadUrl,
        Instant createdAt
) {
}
