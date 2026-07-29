package com.example.demo.signing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_draft", columnList = "draftId"),
        @Index(name = "idx_audit_log_action", columnList = "action")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String draftId;

    @Column
    private String slotId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private Instant timestamp;

    @Column
    private String ip;

    @Column(length = 512)
    private String userAgent;

    @Lob
    @Column
    private String details;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDraftId() { return draftId; }
    public void setDraftId(String draftId) { this.draftId = draftId; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
