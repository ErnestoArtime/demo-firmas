package com.example.demo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.exception.BadRequestException;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.model.TemplateType;

@Service
public class TemplateConversionService {

    private final FileStorageService fileStorageService;
    private final String sofficeCommand;
    private final Duration timeout;

    public TemplateConversionService(
            FileStorageService fileStorageService,
            @Value("${app.convert.soffice-command:soffice}") String sofficeCommand,
            @Value("${app.convert.timeout-seconds:90}") long timeoutSeconds) {
        this.fileStorageService = fileStorageService;
        this.sofficeCommand = sofficeCommand;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public TemplateMetadata convertAndStoreAsDocx(MultipartFile sourceFile) {
        return convertAndStore(sourceFile, TemplateType.DOCX);
    }

    public TemplateMetadata convertAndStore(MultipartFile sourceFile, TemplateType targetType) {
        if (sourceFile == null || sourceFile.isEmpty()) {
            throw new BadRequestException("Debe enviar un archivo");
        }

        String filename = sourceFile.getOriginalFilename() == null ? "" : sourceFile.getOriginalFilename();
        String ext = extensionOf(filename).toLowerCase(Locale.ROOT);
        if (!ext.equals("rtf") && !ext.equals("doc")) {
            throw new BadRequestException("Solo se puede convertir .rtf o .doc");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("template-convert-");
            byte[] outputBytes = convertBytes(sourceFile.getBytes(), ext, targetType, tempDir);

            String baseName = baseName(filename);
            if (baseName.isBlank()) {
                baseName = "template_convertida";
            }
            return fileStorageService.storeTemplateBytes(baseName + "." + targetType.extension(), outputBytes);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo convertir el archivo", ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public byte[] convertDocxToPdf(byte[] docxBytes) {
        if (docxBytes == null || docxBytes.length == 0) {
            throw new BadRequestException("Documento DOCX vacio");
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("docx-pdf-");
            return convertBytes(docxBytes, "docx", TemplateType.PDF, tempDir);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo convertir DOCX a PDF", ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private byte[] convertBytes(byte[] inputBytes, String inputExt, TemplateType targetType, Path tempDir)
            throws IOException {
        Path input = tempDir.resolve("input." + inputExt);
        Files.write(input, inputBytes);

        runSofficeConversion(input, tempDir, targetType);

        Path output = tempDir.resolve("input." + targetType.extension());
        if (!Files.exists(output)) {
            throw new IllegalStateException("LibreOffice no genero archivo de salida");
        }
        return Files.readAllBytes(output);
    }

    private void runSofficeConversion(Path input, Path outputDir, TemplateType targetType) {
        try {
            List<String> command = new ArrayList<>();
            command.add(sofficeCommand);
            command.add("--headless");
            command.add("--convert-to");
            command.add(targetType.extension());
            command.add("--outdir");
            command.add(outputDir.toString());
            command.add(input.toString());

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Timeout ejecutando conversion con LibreOffice");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("LibreOffice devolvio error durante la conversion");
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo ejecutar LibreOffice. Verifica 'soffice' instalado y en PATH", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Conversion interrumpida", ex);
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1);
    }

    private String baseName(String filename) {
        String onlyName = Path.of(filename).getFileName().toString();
        int dot = onlyName.lastIndexOf('.');
        if (dot < 0) {
            return onlyName;
        }
        return onlyName.substring(0, dot);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
