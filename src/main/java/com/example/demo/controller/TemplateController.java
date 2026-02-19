package com.example.demo.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.TemplateUploadResponse;
import com.example.demo.dto.TemplateRequirementsResponse;
import com.example.demo.dto.CatalogFieldResponse;
import com.example.demo.dto.TemplateMappingRequest;
import com.example.demo.dto.TemplateMappingResponse;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.model.TemplateMappingMetadata;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.SampleTemplateService;
import com.example.demo.service.TemplateMappingService;
import com.example.demo.service.TemplateConversionService;
import com.example.demo.service.TemplateRequirementsService;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final FileStorageService fileStorageService;
    private final SampleTemplateService sampleTemplateService;
    private final TemplateRequirementsService templateRequirementsService;
    private final TemplateConversionService templateConversionService;
    private final TemplateMappingService templateMappingService;

    public TemplateController(
            FileStorageService fileStorageService,
            SampleTemplateService sampleTemplateService,
            TemplateRequirementsService templateRequirementsService,
            TemplateConversionService templateConversionService,
            TemplateMappingService templateMappingService) {
        this.fileStorageService = fileStorageService;
        this.sampleTemplateService = sampleTemplateService;
        this.templateRequirementsService = templateRequirementsService;
        this.templateConversionService = templateConversionService;
        this.templateMappingService = templateMappingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateUploadResponse uploadTemplate(@RequestPart("file") MultipartFile file) {
        TemplateMetadata metadata = fileStorageService.storeTemplate(file);
        return mapUploadResponse(metadata);
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateUploadResponse convertTemplate(@RequestPart("file") MultipartFile file) {
        TemplateMetadata metadata = templateConversionService.convertAndStoreAsDocx(file);
        return mapUploadResponse(metadata);
    }

    @GetMapping
    public List<TemplateUploadResponse> listTemplates() {
        return fileStorageService.listTemplates()
                .stream()
                .map(this::mapUploadResponse)
                .toList();
    }

    @GetMapping("/catalog-fields")
    public List<CatalogFieldResponse> catalogFields() {
        return templateMappingService.catalogFields();
    }

    @GetMapping("/sample-docx")
    public ResponseEntity<ByteArrayResource> downloadSampleDocx() throws IOException {
        Path samplePath = sampleTemplateService.getSampleDocxPath();
        byte[] content = Files.readAllBytes(samplePath);
        ByteArrayResource resource = new ByteArrayResource(content);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(samplePath.getFileName().toString())
                        .build()
                        .toString())
                .contentLength(content.length)
                .body(resource);
    }

    @GetMapping("/{templateId}/requirements")
    public TemplateRequirementsResponse getRequirements(@PathVariable String templateId) {
        TemplateMetadata template = fileStorageService.loadTemplate(templateId);
        return new TemplateRequirementsResponse(
                templateId,
                templateRequirementsService.extractRequiredFields(template),
                templateRequirementsService.extractRequiredSignatures(template));
    }

    @GetMapping("/{templateId}/mapping")
    public TemplateMappingResponse getMapping(@PathVariable String templateId) {
        fileStorageService.loadTemplate(templateId);
        TemplateMappingMetadata mapping = templateMappingService.loadOrEmpty(templateId);
        return new TemplateMappingResponse(
                templateId,
                mapping.fieldMappings(),
                mapping.signatureMappings());
    }

    @PostMapping("/{templateId}/mapping")
    public TemplateMappingResponse saveMapping(
            @PathVariable String templateId,
            @org.springframework.web.bind.annotation.RequestBody TemplateMappingRequest request) {
        fileStorageService.loadTemplate(templateId);
        TemplateMappingMetadata saved = templateMappingService.save(
                templateId,
                request.fieldMappingsOrEmpty(),
                request.signatureMappingsOrEmpty());
        return new TemplateMappingResponse(
                templateId,
                saved.fieldMappings(),
                saved.signatureMappings());
    }

    private TemplateUploadResponse mapUploadResponse(TemplateMetadata metadata) {
        return new TemplateUploadResponse(
                metadata.id(),
                metadata.originalFilename(),
                metadata.type(),
                metadata.createdAt());
    }
}
