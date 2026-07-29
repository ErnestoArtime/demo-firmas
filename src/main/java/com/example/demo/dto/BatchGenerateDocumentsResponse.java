package com.example.demo.dto;

import java.time.Instant;
import java.util.List;

import com.example.demo.model.TemplateType;

public record BatchGenerateDocumentsResponse(
        int total,
        int successCount,
        int errorCount,
        List<BatchGenerateDocumentsItem> items) {

    public record BatchGenerateDocumentsItem(
            int index,
            String status,
            String templateId,
            String documentId,
            String downloadUrl,
            TemplateType type,
            Instant createdAt,
            String message) {
    }
}
