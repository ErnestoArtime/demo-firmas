package com.example.demo.signing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "signer_slot", indexes = {
        @Index(name = "idx_signer_slot_token", columnList = "token", unique = true),
        @Index(name = "idx_signer_slot_draft", columnList = "draft_id")
})
public class SignerSlot {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private Draft draft;

    @Column(nullable = false)
    private int rowIndex;

    @Column
    private String signerName;

    @Column
    private String signerEmail;

    @Column
    private String signerNif;

    @Column(nullable = false)
    private String signatureKey;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private Instant tokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignerSlotStatus status;

    @Lob
    @Column(name = "signature_base64")
    private String signatureBase64;

    @Column
    private Instant signedAt;

    @Column
    private String signedFromIp;

    @Column(length = 512)
    private String signedUserAgent;

    @Column(length = 128)
    private String signatureSha256;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Draft getDraft() { return draft; }
    public void setDraft(Draft draft) { this.draft = draft; }
    public int getRowIndex() { return rowIndex; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public String getSignerNif() { return signerNif; }
    public void setSignerNif(String signerNif) { this.signerNif = signerNif; }
    public String getSignatureKey() { return signatureKey; }
    public void setSignatureKey(String signatureKey) { this.signatureKey = signatureKey; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public SignerSlotStatus getStatus() { return status; }
    public void setStatus(SignerSlotStatus status) { this.status = status; }
    public String getSignatureBase64() { return signatureBase64; }
    public void setSignatureBase64(String signatureBase64) { this.signatureBase64 = signatureBase64; }
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
    public String getSignedFromIp() { return signedFromIp; }
    public void setSignedFromIp(String signedFromIp) { this.signedFromIp = signedFromIp; }
    public String getSignedUserAgent() { return signedUserAgent; }
    public void setSignedUserAgent(String signedUserAgent) { this.signedUserAgent = signedUserAgent; }
    public String getSignatureSha256() { return signatureSha256; }
    public void setSignatureSha256(String signatureSha256) { this.signatureSha256 = signatureSha256; }
}
