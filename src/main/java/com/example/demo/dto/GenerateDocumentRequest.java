package com.example.demo.dto;

import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

public class GenerateDocumentRequest {

    @NotBlank(message = "templateId es obligatorio")
    private String templateId;

    private Map<String, String> fields;
    private Map<String, String> signatures;
    private String dataJson;
    private Set<String> requiredFieldKeys;
    private Set<String> requiredSignatureKeys;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }

    public Map<String, String> getSignatures() {
        return signatures;
    }

    public void setSignatures(Map<String, String> signatures) {
        this.signatures = signatures;
    }

    public Map<String, String> fieldsOrEmpty() {
        return fields == null ? Map.of() : fields;
    }

    public Map<String, String> signaturesOrEmpty() {
        return signatures == null ? Map.of() : signatures;
    }

    public String getDataJson() {
        return dataJson;
    }

    public void setDataJson(String dataJson) {
        this.dataJson = dataJson;
    }

    public boolean hasDataJson() {
        return dataJson != null && !dataJson.isBlank();
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
