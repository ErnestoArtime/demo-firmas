package com.example.demo.signing.dto;

import jakarta.validation.constraints.NotBlank;

public class SignByTokenRequest {

    @NotBlank(message = "signatureBase64 es obligatorio")
    private String signatureBase64;

    public String getSignatureBase64() {
        return signatureBase64;
    }

    public void setSignatureBase64(String signatureBase64) {
        this.signatureBase64 = signatureBase64;
    }
}
