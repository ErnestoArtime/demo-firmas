package com.example.demo.signing;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.signing.dto.AuditLogEntry;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String action, String draftId, String slotId, HttpServletRequest request, String details) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setDraftId(draftId);
        entry.setSlotId(slotId);
        entry.setTimestamp(Instant.now());
        if (request != null) {
            entry.setIp(extractClientIp(request));
            String ua = request.getHeader("User-Agent");
            entry.setUserAgent(ua == null ? null : truncate(ua, 500));
        }
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }

    public List<AuditLogEntry> listForDraft(String draftId) {
        return auditLogRepository.findByDraftIdOrderByTimestampAsc(draftId).stream()
                .map(this::toDto)
                .toList();
    }

    private AuditLogEntry toDto(AuditLog log) {
        return new AuditLogEntry(
                log.getId(),
                log.getDraftId(),
                log.getSlotId(),
                log.getAction(),
                log.getTimestamp(),
                log.getIp(),
                log.getUserAgent(),
                log.getDetails());
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
