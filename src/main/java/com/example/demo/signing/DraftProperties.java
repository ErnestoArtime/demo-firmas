package com.example.demo.signing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.drafts")
public class DraftProperties {

    private int tokenTtlDays = 7;
    private String publicBaseUrl = "http://localhost:4200";

    public int getTokenTtlDays() {
        return tokenTtlDays;
    }

    public void setTokenTtlDays(int tokenTtlDays) {
        this.tokenTtlDays = tokenTtlDays;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
