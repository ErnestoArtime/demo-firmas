package com.example.demo.dto;

import java.util.Map;
import java.util.Set;

public record TemplateMappingResponse(
        String templateId,
        Map<String, String> fieldMappings,
        Map<String, String> signatureMappings,
        Set<String> requiredFieldKeys,
        Set<String> requiredSignatureKeys
) {
}
