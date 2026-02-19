package com.example.demo.model;

import java.time.Instant;
import java.util.Map;

public record TemplateMappingMetadata(
        String templateId,
        Map<String, String> fieldMappings,
        Map<String, String> signatureMappings,
        Instant updatedAt
) {
}
