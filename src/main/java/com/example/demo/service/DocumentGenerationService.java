package com.example.demo.service;

import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import com.example.demo.dto.GenerateDocumentRequest;
import com.example.demo.exception.BadRequestException;
import com.example.demo.model.GeneratedDocumentMetadata;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.model.TemplateMappingMetadata;
import com.example.demo.service.engine.GeneratedFile;
import com.example.demo.service.engine.TemplateEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DocumentGenerationService {

    private static final Pattern DATA_URI_BASE64_PATTERN =
            Pattern.compile("^data:[^;]+;base64,(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEXED_SIGNATURE_PATTERN = Pattern.compile("^FIRMA_\\d+$");
    private static final int MAX_SIGNATURE_BYTES = 2_000_000;

    private final FileStorageService fileStorageService;
    private final TemplateRequirementsService templateRequirementsService;
    private final TemplateMappingService templateMappingService;
    private final List<TemplateEngine> templateEngines;
    private final ObjectMapper objectMapper;

    public DocumentGenerationService(
            FileStorageService fileStorageService,
            TemplateRequirementsService templateRequirementsService,
            TemplateMappingService templateMappingService,
            List<TemplateEngine> templateEngines,
            ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.templateRequirementsService = templateRequirementsService;
        this.templateMappingService = templateMappingService;
        this.templateEngines = templateEngines;
        this.objectMapper = objectMapper;
    }

    public GeneratedDocumentMetadata generate(GenerateDocumentRequest request) {
        TemplateMetadata template = fileStorageService.loadTemplate(request.getTemplateId());
        TemplateEngine engine = templateEngines.stream()
                .filter(it -> it.supports(template.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe motor para tipo " + template.type()));

        ParsedDataJson parsedDataJson = parseDataJson(request);
        TemplateMappingMetadata mapping = templateMappingService.loadOrEmpty(template.id());
        Map<String, String> fields = buildFields(request, parsedDataJson, mapping);
        Map<String, String> signaturesBase64 = buildSignatures(request, parsedDataJson, mapping);

        validateRequiredFields(template, fields.keySet());
        Set<String> requiredSignatures = templateRequirementsService.extractRequiredSignatures(template);
        applySignatureFallbacks(signaturesBase64, requiredSignatures);
        validateRequiredSignatures(requiredSignatures, signaturesBase64.keySet());
        Map<String, byte[]> signatures = decodeSignatures(signaturesBase64);

        GeneratedFile generatedFile = engine.generate(
                Path.of(template.filePath()),
                fields,
                signatures);

        return fileStorageService.storeGeneratedDocument(
                template.id(),
                generatedFile.type(),
                generatedFile.content());
    }

    private Map<String, String> buildFields(
            GenerateDocumentRequest request,
            ParsedDataJson parsedDataJson,
            TemplateMappingMetadata mapping) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.putAll(parsedDataJson.fields());
        applyMappedValues(mapping.fieldMappings(), parsedDataJson.fields(), fields);
        fields.putAll(request.fieldsOrEmpty());
        return fields;
    }

    private Map<String, String> buildSignatures(
            GenerateDocumentRequest request,
            ParsedDataJson parsedDataJson,
            TemplateMappingMetadata mapping) {
        Map<String, String> signatures = new LinkedHashMap<>();
        signatures.putAll(parsedDataJson.signatures());
        applyMappedValues(mapping.signatureMappings(), parsedDataJson.signatures(), signatures);
        signatures.putAll(request.signaturesOrEmpty());
        return signatures;
    }

    private void applyMappedValues(
            Map<String, String> mapping,
            Map<String, String> sourceValues,
            Map<String, String> targetValues) {
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String templateVariable = entry.getKey();
            String sourceCode = entry.getValue();
            if (templateVariable == null || templateVariable.isBlank() || sourceCode == null || sourceCode.isBlank()) {
                continue;
            }
            String value = sourceValues.get(sourceCode.toUpperCase());
            if (value != null && !value.isBlank()) {
                targetValues.put(templateVariable, value);
            }
        }
    }

    private ParsedDataJson parseDataJson(GenerateDocumentRequest request) {
        if (!request.hasDataJson()) {
            return ParsedDataJson.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(request.getDataJson());
            if (root == null || root.isNull()) {
                return ParsedDataJson.empty();
            }

            Map<String, String> fields = new LinkedHashMap<>();
            Map<String, String> signatures = new LinkedHashMap<>();

            flattenJsonForFields(root, "", fields);
            extractStudentsDerivedData(root, fields, signatures);

            return new ParsedDataJson(fields, signatures);
        } catch (Exception ex) {
            throw new BadRequestException("dataJson no es un JSON valido");
        }
    }

    private void flattenJsonForFields(JsonNode node, String prefix, Map<String, String> target) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            if (!prefix.isBlank()) {
                target.put(prefix.toUpperCase(), node.asText(""));
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String nextPrefix = prefix.isBlank() ? entry.getKey() : prefix + "_" + entry.getKey();
                flattenJsonForFields(entry.getValue(), nextPrefix, target);
            });
            return;
        }
        if (node.isArray()) {
            int idx = 1;
            for (JsonNode item : node) {
                String nextPrefix = prefix.isBlank() ? String.valueOf(idx) : prefix + "_" + idx;
                flattenJsonForFields(item, nextPrefix, target);
                idx++;
            }
        }
    }

    private void extractStudentsDerivedData(
            JsonNode root,
            Map<String, String> fields,
            Map<String, String> signatures) {
        JsonNode studentsNode = firstArray(root, "alumnos", "students");
        if (studentsNode == null) {
            return;
        }

        List<JsonNode> students = StreamSupport.stream(studentsNode.spliterator(), false).toList();
        if (students.isEmpty()) {
            return;
        }

        fields.put("BD_NOMBRES", joinStudentAttribute(students, "NOMBRE", "NAME", "FIRST_NAME"));
        fields.put("BD_APELLIDOS", joinStudentAttribute(students, "APELLIDOS", "SURNAME", "LAST_NAME"));
        fields.put("BD_NIFS", joinStudentAttribute(students, "NIF", "NIFS", "DNI", "DOCUMENTO"));

        int index = 1;
        for (JsonNode student : students) {
            putIfPresent(fields, "ALUMNO_" + index + "_NOMBRE", findText(student, "NOMBRE", "NAME", "FIRST_NAME"));
            putIfPresent(fields, "ALUMNO_" + index + "_APELLIDOS", findText(student, "APELLIDOS", "SURNAME", "LAST_NAME"));
            putIfPresent(fields, "ALUMNO_" + index + "_NIF", findText(student, "NIF", "NIFS", "DNI", "DOCUMENTO"));

            String signature = findText(student, "FIRMA", "SIGNATURE");
            if (signature != null && !signature.isBlank()) {
                signatures.put("FIRMA_" + index, signature);
            }
            index++;
        }
    }

    private JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && node.isArray()) {
                return node;
            }
            JsonNode upperNode = root.get(name.toUpperCase());
            if (upperNode != null && upperNode.isArray()) {
                return upperNode;
            }
        }
        return null;
    }

    private String joinStudentAttribute(List<JsonNode> students, String... keys) {
        return students.stream()
                .map(student -> findText(student, keys))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String findText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null) {
                value = node.get(key.toLowerCase());
            }
            if (value == null) {
                value = node.get(key.toUpperCase());
            }
            if (value != null && value.isValueNode()) {
                return value.asText("");
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    private void validateRequiredFields(TemplateMetadata template, Set<String> providedFieldKeys) {
        Set<String> required = templateRequirementsService.extractRequiredFields(template);
        if (required.isEmpty()) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(providedFieldKeys);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Faltan campos requeridos: " + String.join(", ", missing));
        }
    }

    private void validateRequiredSignatures(TemplateMetadata template, Set<String> providedSignatureKeys) {
        Set<String> required = templateRequirementsService.extractRequiredSignatures(template);
        validateRequiredSignatures(required, providedSignatureKeys);
    }

    private void validateRequiredSignatures(Set<String> required, Set<String> providedSignatureKeys) {
        if (required.isEmpty()) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(providedSignatureKeys);
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "Faltan firmas requeridas: "
                            + String.join(", ", missing)
                            + ". Sube cada firma o envia alumnos[].firma/base64 en dataJson.");
        }
    }

    private void applySignatureFallbacks(Map<String, String> signatures, Set<String> requiredSignatures) {
        if (requiredSignatures.isEmpty() || signatures.isEmpty()) {
            return;
        }

        String defaultSignature = signatures.get("FIRMA_DEFAULT");
        if (defaultSignature != null && !defaultSignature.isBlank()) {
            for (String key : requiredSignatures) {
                signatures.putIfAbsent(key, defaultSignature);
            }
            return;
        }

        List<String> requiredIndexed = requiredSignatures.stream()
                .filter(key -> INDEXED_SIGNATURE_PATTERN.matcher(key).matches())
                .toList();
        if (requiredIndexed.size() != requiredSignatures.size()) {
            return;
        }

        Set<String> providedRequired = new LinkedHashSet<>(requiredSignatures);
        providedRequired.retainAll(signatures.keySet());
        if (providedRequired.size() != 1) {
            return;
        }

        String singleKey = providedRequired.iterator().next();
        String singleValue = signatures.get(singleKey);
        if (singleValue == null || singleValue.isBlank()) {
            return;
        }

        for (String key : requiredSignatures) {
            signatures.putIfAbsent(key, singleValue);
        }
    }

    private Map<String, byte[]> decodeSignatures(Map<String, String> signatures) {
        return signatures.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> decodeBase64Signature(entry.getValue())));
    }

    private byte[] decodeBase64Signature(String rawBase64) {
        if (rawBase64 == null || rawBase64.isBlank()) {
            throw new BadRequestException("Firma base64 vacia");
        }
        String cleaned = rawBase64.trim();
        Matcher matcher = DATA_URI_BASE64_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            cleaned = matcher.group(1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(cleaned);
            if (bytes.length > MAX_SIGNATURE_BYTES) {
                throw new BadRequestException("La firma supera el tamano maximo permitido");
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Firma con formato base64 invalido");
        }
    }

    private record ParsedDataJson(Map<String, String> fields, Map<String, String> signatures) {
        private static ParsedDataJson empty() {
            return new ParsedDataJson(Map.of(), Map.of());
        }
    }
}
