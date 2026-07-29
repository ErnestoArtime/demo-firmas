package com.example.demo.signing.dto;

import java.time.Instant;

public record AuditLogEntry(
        Long id,
        String draftId,
        String slotId,
        String action,
        Instant timestamp,
        String ip,
        String userAgent,
        String details) {
}
