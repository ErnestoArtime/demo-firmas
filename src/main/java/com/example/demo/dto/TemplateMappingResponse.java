package com.example.demo.dto;

import java.util.Map;

public record TemplateMappingResponse(
        String templateId,
        Map<String, String> fieldMappings,
        Map<String, String> signatureMappings
) {
}
