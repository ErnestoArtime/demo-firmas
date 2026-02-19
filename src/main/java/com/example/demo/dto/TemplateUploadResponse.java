package com.example.demo.dto;

import java.time.Instant;

import com.example.demo.model.TemplateType;

public record TemplateUploadResponse(
        String templateId,
        String originalFilename,
        TemplateType type,
        Instant createdAt
) {
}
