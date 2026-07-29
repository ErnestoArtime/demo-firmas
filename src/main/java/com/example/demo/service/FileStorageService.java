package com.example.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.GeneratedDocumentMetadata;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.model.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FileStorageService {

    private final Path templatesDir;
    private final Path outputDir;
    private final ObjectMapper objectMapper;
    private final long maxTemplateBytes;
    private final long maxGeneratedBytes;

    public FileStorageService(
            @Value("${app.storage.templates-dir:storage/templates}") String templatesDir,
            @Value("${app.storage.output-dir:storage/output}") String outputDir,
            @Value("${app.storage.max-template-bytes:10485760}") long maxTemplateBytes,
            @Value("${app.storage.max-generated-bytes:20971520}") long maxGeneratedBytes,
            ObjectMapper objectMapper) {
        this.templatesDir = Path.of(templatesDir).toAbsolutePath().normalize();
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
        this.maxTemplateBytes = maxTemplateBytes;
        this.maxGeneratedBytes = maxGeneratedBytes;
        this.objectMapper = objectMapper;
        createDirectories();
    }

    public TemplateMetadata storeTemplate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Debe enviar un archivo");
        }
        try (InputStream in = file.getInputStream()) {
            return storeTemplateBytes(sanitizeFilename(file.getOriginalFilename()), in.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la plantilla", ex);
        }
    }

    public TemplateMetadata storeTemplateBytes(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("Debe enviar un archivo con contenido");
        }
        if (content.length > maxTemplateBytes) {
            throw new BadRequestException("La plantilla supera el tamano maximo permitido");
        }

        String safeFilename = sanitizeFilename(originalFilename);
        TemplateType type = TemplateType.fromFilename(safeFilename);

        String id = UUID.randomUUID().toString();
        Path targetFile = templatesDir.resolve(id + "." + type.extension());
        try {
            Files.write(targetFile, content);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la plantilla", ex);
        }

        TemplateMetadata metadata = new TemplateMetadata(
                id,
                safeFilename,
                type,
                targetFile.toString(),
                Instant.now());
        writeJson(templatesDir.resolve(id + ".json"), metadata);
        return metadata;
    }

    public TemplateMetadata loadTemplate(String id) {
        return readJson(templatesDir.resolve(id + ".json"), TemplateMetadata.class,
                "No existe la plantilla con id: " + id);
    }

    public List<TemplateMetadata> listTemplates() {
        try (Stream<Path> paths = Files.list(templatesDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> readJson(path, TemplateMetadata.class, "Metadata de plantilla no encontrado"))
                    .sorted(Comparator.comparing(TemplateMetadata::createdAt).reversed())
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo listar plantillas", ex);
        }
    }

    public GeneratedDocumentMetadata storeGeneratedDocument(
            String templateId,
            TemplateType type,
            byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("No se genero contenido para el documento");
        }
        if (content.length > maxGeneratedBytes) {
            throw new BadRequestException("El documento generado supera el tamano maximo permitido");
        }

        String id = UUID.randomUUID().toString();
        String outputFilename = "documento_" + id + "." + type.extension();
        Path outputFile = outputDir.resolve(outputFilename);

        try {
            Files.write(outputFile, content);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar el documento generado", ex);
        }

        GeneratedDocumentMetadata metadata = new GeneratedDocumentMetadata(
                id,
                templateId,
                type,
                outputFile.toString(),
                outputFilename,
                Instant.now());
        writeJson(outputDir.resolve(id + ".json"), metadata);
        return metadata;
    }

    public GeneratedDocumentMetadata loadGeneratedDocument(String documentId) {
        return readJson(outputDir.resolve(documentId + ".json"), GeneratedDocumentMetadata.class,
                "No existe el documento generado con id: " + documentId);
    }

    public byte[] loadFileContent(String absolutePath) {
        Path normalized = Path.of(absolutePath).toAbsolutePath().normalize();
        if (!normalized.startsWith(templatesDir) && !normalized.startsWith(outputDir)) {
            throw new BadRequestException("Ruta de archivo no permitida");
        }

        try {
            return Files.readAllBytes(normalized);
        } catch (IOException ex) {
            throw new NotFoundException("No se pudo leer el archivo solicitado");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("Nombre de archivo invalido");
        }
        String onlyName = Path.of(filename).getFileName().toString().replaceAll("[\\r\\n]", "_").trim();
        if (onlyName.isBlank()) {
            throw new BadRequestException("Nombre de archivo invalido");
        }
        return onlyName;
    }

    private void writeJson(Path path, Object value) {
        try {
            objectMapper.writeValue(path.toFile(), value);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar metadatos", ex);
        }
    }

    private <T> T readJson(Path path, Class<T> type, String notFoundMessage) {
        if (!Files.exists(path)) {
            throw new NotFoundException(notFoundMessage);
        }
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer metadatos", ex);
        }
    }

    private void createDirectories() {
        try {
            Files.createDirectories(templatesDir);
            Files.createDirectories(outputDir);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear la estructura de almacenamiento", ex);
        }
    }
}