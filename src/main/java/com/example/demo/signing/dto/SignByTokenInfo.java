package com.example.demo.signing.dto;

import java.time.Instant;

import com.example.demo.signing.DraftStatus;
import com.example.demo.signing.SignerSlotStatus;

public record SignByTokenInfo(
        String draftId,
        String title,
        DraftStatus draftStatus,
        SignerSlotStatus slotStatus,
        int rowIndex,
        String signerName,
        String signerNif,
        String signatureKey,
        Instant tokenExpiresAt,
        Instant signedAt) {
}
