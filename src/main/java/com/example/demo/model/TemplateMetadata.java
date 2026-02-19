package com.example.demo.model;

import java.time.Instant;

public record TemplateMetadata(
        String id,
        String originalFilename,
        TemplateType type,
        String filePath,
        Instant createdAt
) {
}
