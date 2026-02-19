package com.example.demo.dto;

import java.util.Map;
import java.util.Set;

public class TemplateMappingRequest {

    private Map<String, String> fieldMappings;
    private Map<String, String> signatureMappings;
    private Set<String> requiredFieldKeys;
    private Set<String> requiredSignatureKeys;

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

    public Set<String> getRequiredFieldKeys() {
        return requiredFieldKeys;
    }

    public void setRequiredFieldKeys(Set<String> requiredFieldKeys) {
        this.requiredFieldKeys = requiredFieldKeys;
    }

    public Set<String> requiredFieldKeysOrEmpty() {
        return requiredFieldKeys == null ? Set.of() : requiredFieldKeys;
    }

    public Set<String> getRequiredSignatureKeys() {
        return requiredSignatureKeys;
    }

    public void setRequiredSignatureKeys(Set<String> requiredSignatureKeys) {
        this.requiredSignatureKeys = requiredSignatureKeys;
    }

    public Set<String> requiredSignatureKeysOrEmpty() {
        return requiredSignatureKeys == null ? Set.of() : requiredSignatureKeys;
    }
}
