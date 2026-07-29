package com.example.demo.signing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "draft")
public class Draft {

    @Id
    private String id;

    @Column(nullable = false)
    private String templateId;

    @Column
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DraftStatus status;

    @Lob
    @Column(name = "fields_json")
    private String fieldsJson;

    @Lob
    @Column(name = "data_json")
    private String dataJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant finalizedAt;

    @Column
    private String generatedDocumentId;

    @Column(nullable = false)
    private String outputType;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("rowIndex ASC")
    private List<SignerSlot> signers = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public DraftStatus getStatus() { return status; }
    public void setStatus(DraftStatus status) { this.status = status; }
    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String fieldsJson) { this.fieldsJson = fieldsJson; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }
    public String getGeneratedDocumentId() { return generatedDocumentId; }
    public void setGeneratedDocumentId(String generatedDocumentId) { this.generatedDocumentId = generatedDocumentId; }
    public String getOutputType() { return outputType; }
    public void setOutputType(String outputType) { this.outputType = outputType; }
    public List<SignerSlot> getSigners() { return signers; }
    public void setSigners(List<SignerSlot> signers) { this.signers = signers; }
}
