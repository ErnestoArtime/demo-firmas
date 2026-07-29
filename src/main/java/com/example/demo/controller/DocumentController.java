package com.example.demo.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.BatchGenerateDocumentsRequest;
import com.example.demo.dto.BatchGenerateDocumentsResponse;
import com.example.demo.dto.BatchGenerateDocumentsResponse.BatchGenerateDocumentsItem;
import com.example.demo.dto.GenerateDocumentPreviewResponse;
import com.example.demo.dto.GenerateDocumentRequest;
import com.example.demo.dto.GenerateDocumentResponse;
import com.example.demo.model.GeneratedDocumentMetadata;
import com.example.demo.service.DocumentGenerationService;
import com.example.demo.service.FileStorageService;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentGenerationService documentGenerationService;
    private final FileStorageService fileStorageService;

    public DocumentController(
            DocumentGenerationService documentGenerationService,
            FileStorageService fileStorageService) {
        this.documentGenerationService = documentGenerationService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/generate")
    public GenerateDocumentResponse generate(@Valid @RequestBody GenerateDocumentRequest request) {
        GeneratedDocumentMetadata generated = documentGenerationService.generate(request);
        return new GenerateDocumentResponse(
                generated.id(),
                generated.templateId(),
                generated.type(),
                "/api/documents/" + generated.id(),
                generated.createdAt());
    }

    @PostMapping("/generate/preview")
    public GenerateDocumentPreviewResponse preview(@Valid @RequestBody GenerateDocumentRequest request) {
        return documentGenerationService.preview(request);
    }

    @PostMapping("/generate/batch")
    public BatchGenerateDocumentsResponse generateBatch(
            @Valid @RequestBody BatchGenerateDocumentsRequest request) {
        List<BatchGenerateDocumentsItem> items = new ArrayList<>();
        int successCount = 0;

        int index = 0;
        for (GenerateDocumentRequest itemRequest : request.getRequests()) {
            try {
                if (itemRequest == null) {
                    throw new IllegalArgumentException("La solicitud del lote no puede ser null");
                }
                GeneratedDocumentMetadata generated = documentGenerationService.generate(itemRequest);
                successCount++;
                items.add(new BatchGenerateDocumentsItem(
                        index,
                        "success",
                        generated.templateId(),
                        generated.id(),
                        "/api/documents/" + generated.id(),
                        generated.type(),
                        generated.createdAt(),
                        null));
            } catch (Exception ex) {
                items.add(new BatchGenerateDocumentsItem(
                        index,
                        "error",
                        itemRequest == null ? null : itemRequest.getTemplateId(),
                        null,
                        null,
                        null,
                        null,
                        ex.getMessage() == null ? "Error desconocido" : ex.getMessage()));
            }
            index++;
        }

        return new BatchGenerateDocumentsResponse(
                items.size(),
                successCount,
                items.size() - successCount,
                items);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String documentId) {
        GeneratedDocumentMetadata metadata = fileStorageService.loadGeneratedDocument(documentId);
        byte[] content = fileStorageService.loadFileContent(metadata.filePath());
        ByteArrayResource resource = new ByteArrayResource(content);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.type().mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(metadata.outputFilename())
                        .build()
                        .toString())
                .contentLength(content.length)
                .body(resource);
    }
}
