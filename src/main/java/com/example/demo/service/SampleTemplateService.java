package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SampleTemplateService {

    private static final Logger log = LoggerFactory.getLogger(SampleTemplateService.class);
    private static final String SAMPLE_FILENAME = "plantilla_demo.docx";

    private final Path samplesDir;

    public SampleTemplateService(@Value("${app.samples.dir:storage/samples}") String samplesDir) {
        this.samplesDir = Path.of(samplesDir).toAbsolutePath().normalize();
        try {
            createSampleIfMissing();
        } catch (Exception ex) {
            log.error("No se pudo crear la plantilla DOCX de ejemplo al iniciar. Se reintentara on-demand.", ex);
        }
    }

    public Path getSampleDocxPath() {
        Path samplePath = samplesDir.resolve(SAMPLE_FILENAME);
        if (!isValidDocx(samplePath)) {
            try {
                Files.deleteIfExists(samplePath);
            } catch (IOException ex) {
                throw new IllegalStateException("No se pudo limpiar la plantilla demo invalida", ex);
            }
            createSampleIfMissing();
        }
        return samplePath;
    }

    private void createSampleIfMissing() {
        try {
            Files.createDirectories(samplesDir);
            Path samplePath = samplesDir.resolve(SAMPLE_FILENAME);
            if (isValidDocx(samplePath)) {
                return;
            }
            Files.deleteIfExists(samplePath);

            WordprocessingMLPackage word = WordprocessingMLPackage.createPackage();
            MainDocumentPart main = word.getMainDocumentPart();

            main.addStyledParagraphOfText("Title", "Control de asistencia - plantilla demo");
            main.addParagraphOfText("Nombre: ${NOMBRE}");
            main.addParagraphOfText("Fecha: ${FECHA}");
            main.addParagraphOfText("Curso: ${CURSO}");
            main.addParagraphOfText("Firma 1: ${FIRMA_1}");
            main.addParagraphOfText("Firma 2: ${FIRMA_2}");
            main.addParagraphOfText("Firma 3: ${FIRMA_3}");
            main.addParagraphOfText("");
            main.addParagraphOfText("Nota: usa esta plantilla para probar /api/templates y /api/documents/generate.");

            word.save(samplePath.toFile());
            log.info("Plantilla DOCX de ejemplo creada en {}", samplePath);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear la plantilla de ejemplo", ex);
        }
    }

    private boolean isValidDocx(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        try {
            if (Files.size(path) < 512) {
                return false;
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                var documentEntry = zip.getEntry("word/document.xml");
                if (documentEntry == null) {
                    return false;
                }
                String xml = new String(zip.getInputStream(documentEntry).readAllBytes(), StandardCharsets.UTF_8);
                return xml.contains("${FIRMA_3}");
            }
        } catch (Exception ex) {
            return false;
        }
    }
}
