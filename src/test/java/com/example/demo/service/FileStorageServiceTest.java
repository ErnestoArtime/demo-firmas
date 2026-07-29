package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.demo.model.TemplateMetadata;
import com.example.demo.model.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class FileStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsLegacyMetadataFromTheConfiguredTemplateDirectory() throws Exception {
        Path templatesDirectory = temporaryDirectory.resolve("templates");
        String templateId = "b001fbc0-1934-4fd4-b34f-48963e6be172";
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        FileStorageService storageService = new FileStorageService(
                templatesDirectory.toString(),
                temporaryDirectory.resolve("output").toString(),
                1_048_576,
                1_048_576,
                objectMapper);

        Path storedFile = templatesDirectory.resolve(templateId + ".pdf");
        Files.writeString(storedFile, "template content");
        objectMapper.writeValue(
                templatesDirectory.resolve(templateId + ".json").toFile(),
                new TemplateMetadata(
                        templateId,
                        "plantilla_demo.pdf",
                        TemplateType.PDF,
                        "C:\\Proyectos\\java\\demo\\storage\\templates\\" + templateId + ".pdf",
                        Instant.parse("2026-02-19T07:06:56Z")));

        TemplateMetadata loaded = storageService.loadTemplate(templateId);
        TemplateMetadata persisted = objectMapper.readValue(
                templatesDirectory.resolve(templateId + ".json").toFile(),
                TemplateMetadata.class);

        assertThat(loaded.filePath()).isEqualTo(storedFile.toAbsolutePath().normalize().toString());
        assertThat(persisted.filePath()).isEqualTo(templateId + ".pdf");
    }
}
