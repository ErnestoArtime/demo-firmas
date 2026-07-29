package com.example.demo.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public class BatchGenerateDocumentsRequest {

    @NotEmpty(message = "requests debe contener al menos un elemento")
    private List<GenerateDocumentRequest> requests;

    public List<GenerateDocumentRequest> getRequests() {
        return requests;
    }

    public void setRequests(List<GenerateDocumentRequest> requests) {
        this.requests = requests;
    }
}
