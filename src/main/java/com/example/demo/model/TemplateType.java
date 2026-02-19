package com.example.demo.model;

import java.util.Arrays;

import com.example.demo.exception.BadRequestException;

public enum TemplateType {
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String mediaType;

    TemplateType(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public static TemplateType fromExtension(String extension) {
        return Arrays.stream(values())
                .filter(v -> v.extension.equalsIgnoreCase(extension))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Tipo de archivo no soportado. Solo se permite: .docx o .pdf (.doc no es compatible)"));
    }

    public static TemplateType fromFilename(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            throw new BadRequestException("El archivo no tiene extension valida");
        }
        return fromExtension(filename.substring(lastDot + 1));
    }
}
