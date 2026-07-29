package com.example.demo.signing.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public class CreateDraftRequest {

    @NotBlank(message = "templateId es obligatorio")
    private String templateId;

    private String title;

    private Map<String, String> fields;

    private String dataJson;

    private String outputType;

    @Valid
    @NotEmpty(message = "Debes incluir al menos un firmante")
    private List<DraftSignerInput> signers;

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public String getOutputType() { return outputType; }
    public void setOutputType(String outputType) { this.outputType = outputType; }
    public List<DraftSignerInput> getSigners() { return signers; }
    public void setSigners(List<DraftSignerInput> signers) { this.signers = signers; }
}
