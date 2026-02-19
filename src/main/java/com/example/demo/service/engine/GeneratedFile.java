package com.example.demo.service.engine;

import com.example.demo.model.TemplateType;

public record GeneratedFile(
        TemplateType type,
        byte[] content
) {
}
