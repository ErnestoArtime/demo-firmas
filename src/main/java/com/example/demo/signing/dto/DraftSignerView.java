package com.example.demo.signing.dto;

import java.time.Instant;

import com.example.demo.signing.SignerSlotStatus;

public record DraftSignerView(
        String slotId,
        int rowIndex,
        String signerName,
        String signerEmail,
        String signerNif,
        String signatureKey,
        SignerSlotStatus status,
        Instant signedAt,
        Instant tokenExpiresAt,
        String shareUrl) {
}
