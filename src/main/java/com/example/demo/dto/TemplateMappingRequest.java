package com.example.demo.dto;

import java.util.Map;

public class TemplateMappingRequest {

    private Map<String, String> fieldMappings;
    private Map<String, String> signatureMappings;

    public Map<String, String> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(Map<String, String> fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

    public Map<String, String> getSignatureMappings() {
        return signatureMappings;
    }

    public void setSignatureMappings(Map<String, String> signatureMappings) {
        this.signatureMappings = signatureMappings;
    }

    public Map<String, String> fieldMappingsOrEmpty() {
        return fieldMappings == null ? Map.of() : fieldMappings;
    }

    public Map<String, String> signatureMappingsOrEmpty() {
        return signatureMappings == null ? Map.of() : signatureMappings;
    }
}
