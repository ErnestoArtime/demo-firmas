package com.example.demo.dto;

import java.util.Set;

public record TemplateRequirementsResponse(
        String templateId,
        Set<String> requiredFields,
        Set<String> requiredSignatures
) {
}
