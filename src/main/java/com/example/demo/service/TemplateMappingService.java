package com.example.demo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.dto.CatalogFieldResponse;
import com.example.demo.model.TemplateMappingMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TemplateMappingService {

    private final Path templatesDir;
    private final ObjectMapper objectMapper;

    public TemplateMappingService(
            @Value("${app.storage.templates-dir:storage/templates}") String templatesDir,
            ObjectMapper objectMapper) {
        this.templatesDir = Path.of(templatesDir).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public TemplateMappingMetadata loadOrEmpty(String templateId) {
        Path filePath = mappingPath(templateId);
        if (!Files.exists(filePath)) {
            return new TemplateMappingMetadata(templateId, Map.of(), Map.of(), Set.of(), Set.of(), Instant.now());
        }
        try {
            TemplateMappingMetadata metadata = objectMapper.readValue(filePath.toFile(), TemplateMappingMetadata.class);
            return new TemplateMappingMetadata(
                    templateId,
                    metadata.fieldMappings() == null ? Map.of() : metadata.fieldMappings(),
                    metadata.signatureMappings() == null ? Map.of() : metadata.signatureMappings(),
                    metadata.requiredFieldKeys() == null ? Set.of() : metadata.requiredFieldKeys(),
                    metadata.requiredSignatureKeys() == null ? Set.of() : metadata.requiredSignatureKeys(),
                    metadata.updatedAt() == null ? Instant.now() : metadata.updatedAt());
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer mapping de plantilla", ex);
        }
    }

    public TemplateMappingMetadata save(
            String templateId,
            Map<String, String> fieldMappings,
            Map<String, String> signatureMappings,
            Set<String> requiredFieldKeys,
            Set<String> requiredSignatureKeys) {
        TemplateMappingMetadata metadata = new TemplateMappingMetadata(
                templateId,
                normalizeMap(fieldMappings),
                normalizeMap(signatureMappings),
                normalizeSet(requiredFieldKeys),
                normalizeSet(requiredSignatureKeys),
                Instant.now());
        try {
            objectMapper.writeValue(mappingPath(templateId).toFile(), metadata);
            return metadata;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar mapping de plantilla", ex);
        }
    }

    public List<CatalogFieldResponse> catalogFields() {
        return List.of(
                new CatalogFieldResponse("CURSO_NOMBRE", "Nombre del curso", "field"),
                new CatalogFieldResponse("CURSO_FECHA", "Fecha del curso o sesion", "field"),
                new CatalogFieldResponse("CURSO_TUTOR", "Tutor del curso", "field"),
                new CatalogFieldResponse("EMPRESA_NOMBRE", "Nombre de empresa o cliente", "field"),
                new CatalogFieldResponse("EMPRESA_CIF", "CIF/NIF de empresa", "field"),
                new CatalogFieldResponse("BD_NOMBRES", "Listado de nombres de alumnos", "field"),
                new CatalogFieldResponse("BD_APELLIDOS", "Listado de apellidos de alumnos", "field"),
                new CatalogFieldResponse("BD_NIFS", "Listado de NIFs de alumnos", "field"),
                new CatalogFieldResponse("ALUMNO_1_NOMBRE", "Nombre del alumno 1 (fila base)", "field"),
                new CatalogFieldResponse("ALUMNO_1_APELLIDOS", "Apellidos del alumno 1 (fila base)", "field"),
                new CatalogFieldResponse("ALUMNO_1_NIF", "NIF del alumno 1 (fila base)", "field"),
                new CatalogFieldResponse("FIRMA_1", "Firma del alumno 1 (fila base)", "signature"),
                new CatalogFieldResponse("FIRMA_DEFAULT", "Firma por defecto para todos", "signature"));
    }

    private Path mappingPath(String templateId) {
        return templatesDir.resolve(templateId + ".mapping.json");
    }

    private Map<String, String> normalizeMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim().toUpperCase(Locale.ROOT);
            if (key.isBlank() || value.isBlank()) {
                continue;
            }
            normalized.put(key, value);
        }
        return normalized;
    }

    private Set<String> normalizeSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String value : source) {
            String current = value == null ? "" : value.trim();
            if (!current.isBlank()) {
                normalized.add(current);
            }
        }
        return normalized;
    }
}