package com.example.demo.service.engine;

import java.nio.file.Path;
import java.util.Map;

import com.example.demo.model.TemplateType;

public interface TemplateEngine {
    boolean supports(TemplateType type);

    GeneratedFile generate(Path templatePath, Map<String, String> fields, Map<String, byte[]> signatures);
}
