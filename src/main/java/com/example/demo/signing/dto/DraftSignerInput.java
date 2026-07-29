package com.example.demo.signing.dto;

public class DraftSignerInput {

    private String signerName;
    private String signerEmail;
    private String signerNif;
    private String signatureKey;

    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public String getSignerNif() { return signerNif; }
    public void setSignerNif(String signerNif) { this.signerNif = signerNif; }
    public String getSignatureKey() { return signatureKey; }
    public void setSignatureKey(String signatureKey) { this.signatureKey = signatureKey; }
}
