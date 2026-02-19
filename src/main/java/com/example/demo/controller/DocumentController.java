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

import com.example.demo.dto.GenerateDocumentRequest;
import com.example.demo.dto.GenerateDocumentResponse;
import com.example.demo.model.GeneratedDocumentMetadata;
import com.example.demo.service.DocumentGenerationService;
import com.example.demo.service.FileStorageService;

import jakarta.validation.Valid;

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
