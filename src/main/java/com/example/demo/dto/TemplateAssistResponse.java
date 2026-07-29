package com.example.demo.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.demo.model.TemplateType;

public record TemplateAssistResponse(
        String templateId,
        TemplateType templateType,
        Set<String> requiredFields,
        Set<String> requiredSignatures,
        List<CatalogFieldResponse> catalogFields,
        Map<String, List<String>> suggestedFieldMappings,
        Map<String, List<String>> suggestedSignatureMappings) {
}
