package com.example.demo.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.api-key")
public class ApiKeySecurityProperties {

    private boolean enabled = false;
    private String mode = "plain";
    private String headerName = "X-API-Key";
    private List<String> keys = List.of();

    private String keyIdHeaderName = "X-API-Key-Id";
    private String signatureHeaderName = "X-API-Signature";
    private String timestampHeaderName = "X-API-Timestamp";
    private String nonceHeaderName = "X-API-Nonce";
    private List<String> hmacClients = List.of();
    private long maxSkewSeconds = 300;
    private boolean requireNonce = true;
    private boolean requireHttps = false;

    private List<String> publicPaths = List.of(
            "/",
            "/hola",
            "/health",
            "/swagger-ui/**",
            "/v3/api-docs/**");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public String getKeyIdHeaderName() {
        return keyIdHeaderName;
    }

    public void setKeyIdHeaderName(String keyIdHeaderName) {
        this.keyIdHeaderName = keyIdHeaderName;
    }

    public String getSignatureHeaderName() {
        return signatureHeaderName;
    }

    public void setSignatureHeaderName(String signatureHeaderName) {
        this.signatureHeaderName = signatureHeaderName;
    }

    public String getTimestampHeaderName() {
        return timestampHeaderName;
    }

    public void setTimestampHeaderName(String timestampHeaderName) {
        this.timestampHeaderName = timestampHeaderName;
    }

    public String getNonceHeaderName() {
        return nonceHeaderName;
    }

    public void setNonceHeaderName(String nonceHeaderName) {
        this.nonceHeaderName = nonceHeaderName;
    }

    public List<String> getHmacClients() {
        return hmacClients;
    }

    public void setHmacClients(List<String> hmacClients) {
        this.hmacClients = hmacClients;
    }

    public long getMaxSkewSeconds() {
        return maxSkewSeconds;
    }

    public void setMaxSkewSeconds(long maxSkewSeconds) {
        this.maxSkewSeconds = maxSkewSeconds;
    }

    public boolean isRequireNonce() {
        return requireNonce;
    }

    public void setRequireNonce(boolean requireNonce) {
        this.requireNonce = requireNonce;
    }

    public boolean isRequireHttps() {
        return requireHttps;
    }

    public void setRequireHttps(boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}