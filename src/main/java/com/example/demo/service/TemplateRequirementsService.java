package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.stereotype.Service;

import com.example.demo.exception.BadRequestException;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.model.TemplateType;

@Service
public class TemplateRequirementsService {

    private static final Pattern DOCX_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    public Set<String> extractRequiredSignatures(TemplateMetadata template) {
        Set<String> placeholders = extractPlaceholders(template);
        Set<String> signatures = new LinkedHashSet<>();
        for (String key : placeholders) {
            if (isSignatureKey(key)) {
                signatures.add(key);
            }
        }
        return signatures;
    }

    public Set<String> extractRequiredFields(TemplateMetadata template) {
        Set<String> placeholders = extractPlaceholders(template);
        Set<String> fields = new LinkedHashSet<>();
        for (String key : placeholders) {
            if (!isSignatureKey(key)) {
                fields.add(key);
            }
        }
        return fields;
    }

    private Set<String> extractPlaceholders(TemplateMetadata template) {
        Path path = Path.of(template.filePath());
        if (template.type() == TemplateType.DOCX) {
            return extractDocxPlaceholders(path);
        }
        if (template.type() == TemplateType.PDF) {
            return extractPdfFieldNames(path);
        }
        return Collections.emptySet();
    }

    private Set<String> extractDocxPlaceholders(Path docxPath) {
        Set<String> placeholders = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(docxPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("word/") || !name.endsWith(".xml")) {
                    continue;
                }
                String xml = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                Matcher matcher = DOCX_PLACEHOLDER_PATTERN.matcher(xml);
                while (matcher.find()) {
                    placeholders.add(matcher.group(1));
                }
            }
            return placeholders;
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo analizar la plantilla DOCX");
        }
    }

    private Set<String> extractPdfFieldNames(Path pdfPath) {
        Set<String> fields = new LinkedHashSet<>();
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                return fields;
            }
            for (PDField field : acroForm.getFieldTree()) {
                if (field.getFullyQualifiedName() != null) {
                    fields.add(field.getFullyQualifiedName());
                }
            }
            return fields;
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo analizar la plantilla PDF");
        }
    }

    private boolean isSignatureKey(String key) {
        String normalized = key.toUpperCase();
        return normalized.equals("FIRMA")
                || normalized.startsWith("FIRMA_")
                || normalized.startsWith("SIGNATURE_")
                || normalized.startsWith("SIG_");
    }
}
