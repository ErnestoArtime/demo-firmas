package com.example.demo.model;

import java.time.Instant;

public record GeneratedDocumentMetadata(
        String id,
        String templateId,
        TemplateType type,
        String filePath,
        String outputFilename,
        Instant createdAt
) {
}
