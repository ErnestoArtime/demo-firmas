package com.example.demo.model;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record TemplateMappingMetadata(
        String templateId,
        Map<String, String> fieldMappings,
        Map<String, String> signatureMappings,
        Set<String> requiredFieldKeys,
        Set<String> requiredSignatureKeys,
        Instant updatedAt
) {
}
