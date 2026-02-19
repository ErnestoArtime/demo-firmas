package com.example.demo.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
import com.example.demo.model.TemplateMetadata;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.SampleTemplateService;
import com.example.demo.service.TemplateConversionService;
import com.example.demo.service.TemplateRequirementsService;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final FileStorageService fileStorageService;
    private final SampleTemplateService sampleTemplateService;
    private final TemplateRequirementsService templateRequirementsService;
    private final TemplateConversionService templateConversionService;

    public TemplateController(
            FileStorageService fileStorageService,
            SampleTemplateService sampleTemplateService,
            TemplateRequirementsService templateRequirementsService,
            TemplateConversionService templateConversionService) {
        this.fileStorageService = fileStorageService;
        this.sampleTemplateService = sampleTemplateService;
        this.templateRequirementsService = templateRequirementsService;
        this.templateConversionService = templateConversionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateUploadResponse uploadTemplate(@RequestPart("file") MultipartFile file) {
        TemplateMetadata metadata = fileStorageService.storeTemplate(file);
        return new TemplateUploadResponse(
                metadata.id(),
                metadata.originalFilename(),
                metadata.type(),
                metadata.createdAt());
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateUploadResponse convertTemplate(@RequestPart("file") MultipartFile file) {
        TemplateMetadata metadata = templateConversionService.convertAndStoreAsDocx(file);
        return new TemplateUploadResponse(
                metadata.id(),
                metadata.originalFilename(),
                metadata.type(),
                metadata.createdAt());
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
}
