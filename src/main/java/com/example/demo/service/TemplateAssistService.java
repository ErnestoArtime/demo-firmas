package com.example.demo.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.demo.dto.CatalogFieldResponse;
import com.example.demo.dto.TemplateAssistResponse;
import com.example.demo.model.TemplateMetadata;

@Service
public class TemplateAssistService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_SUGGESTIONS_PER_FIELD = 3;

    private final TemplateRequirementsService templateRequirementsService;
    private final TemplateMappingService templateMappingService;

    public TemplateAssistService(
            TemplateRequirementsService templateRequirementsService,
            TemplateMappingService templateMappingService) {
        this.templateRequirementsService = templateRequirementsService;
        this.templateMappingService = templateMappingService;
    }

    public TemplateAssistResponse buildTemplateAssist(String templateId, TemplateMetadata template) {
        Set<String> requiredFields = templateRequirementsService.extractRequiredFields(template);
        Set<String> requiredSignatures = templateRequirementsService.extractRequiredSignatures(template);
        List<CatalogFieldResponse> catalogFields = templateMappingService.catalogFields();

        Map<String, List<String>> suggestedFieldMappings = suggestMappings(requiredFields, catalogFields, "field");
        Map<String, List<String>> suggestedSignatureMappings = suggestMappings(
                requiredSignatures,
                catalogFields,
                "signature");

        return new TemplateAssistResponse(
                templateId,
                template.type(),
                requiredFields,
                requiredSignatures,
                catalogFields,
                suggestedFieldMappings,
                suggestedSignatureMappings);
    }

    private Map<String, List<String>> suggestMappings(
            Set<String> templateKeys,
            List<CatalogFieldResponse> catalogFields,
            String kind) {
        Map<String, List<String>> suggestions = new LinkedHashMap<>();
        List<CatalogFieldResponse> candidateFields = catalogFields.stream()
                .filter(field -> kind.equalsIgnoreCase(field.kind()))
                .toList();

        for (String templateKey : templateKeys) {
            String normalizedTemplate = normalize(templateKey);
            List<String> ranked = candidateFields.stream()
                    .map(field -> Map.entry(field.code(), similarityScore(normalizedTemplate, normalize(field.code()))))
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(MAX_SUGGESTIONS_PER_FIELD)
                    .map(Map.Entry::getKey)
                    .toList();

            if (!ranked.isEmpty()) {
                suggestions.put(templateKey, ranked);
            }
        }
        return suggestions;
    }

    private int similarityScore(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0;
        }
        if (left.equals(right)) {
            return 100;
        }

        int score = 0;
        if (left.contains(right) || right.contains(left)) {
            score += 60;
        }

        String[] leftTokens = left.split("_");
        String[] rightTokens = right.split("_");
        for (String leftToken : leftTokens) {
            if (leftToken.isBlank()) {
                continue;
            }
            for (String rightToken : rightTokens) {
                if (rightToken.isBlank()) {
                    continue;
                }
                if (leftToken.equals(rightToken)) {
                    score += 20;
                } else if (leftToken.contains(rightToken) || rightToken.contains(leftToken)) {
                    score += 8;
                }
            }
        }
        return Math.min(score, 99);
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = NON_ALNUM.matcher(normalized).replaceAll("_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
