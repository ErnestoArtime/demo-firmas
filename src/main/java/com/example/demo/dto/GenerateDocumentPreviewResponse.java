package com.example.demo.dto;

import java.util.List;
import java.util.Set;

import com.example.demo.model.TemplateType;

public record GenerateDocumentPreviewResponse(
        String templateId,
        TemplateType templateType,
        TemplateType outputType,
        Set<String> requiredFieldKeys,
        Set<String> requiredSignatureKeys,
        Set<String> providedFieldKeys,
        Set<String> providedSignatureKeys,
        Set<String> missingFieldKeys,
        Set<String> missingSignatureKeys,
        List<String> warnings,
        boolean readyToGenerate) {
}
