package com.example.demo.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public class GenerateDocumentRequest {

    @NotBlank(message = "templateId es obligatorio")
    private String templateId;

    private Map<String, String> fields;
    private Map<String, String> signatures;

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
}
