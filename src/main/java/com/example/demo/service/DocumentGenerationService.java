package com.example.demo.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import com.example.demo.dto.GenerateDocumentPreviewResponse;
import com.example.demo.dto.GenerateDocumentRequest;
import com.example.demo.exception.BadRequestException;
import com.example.demo.model.GeneratedDocumentMetadata;
import com.example.demo.model.TemplateMappingMetadata;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.model.TemplateType;
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
    private static final int MAX_FIELDS = 500;
    private static final int MAX_SIGNATURES = 100;
    private static final int MAX_DATA_JSON_CHARS = 500_000;

    private final FileStorageService fileStorageService;
    private final TemplateRequirementsService templateRequirementsService;
    private final TemplateMappingService templateMappingService;
    private final List<TemplateEngine> templateEngines;
    private final TemplateConversionService templateConversionService;
    private final ObjectMapper objectMapper;

    public DocumentGenerationService(
            FileStorageService fileStorageService,
            TemplateRequirementsService templateRequirementsService,
            TemplateMappingService templateMappingService,
            List<TemplateEngine> templateEngines,
            TemplateConversionService templateConversionService,
            ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.templateRequirementsService = templateRequirementsService;
        this.templateMappingService = templateMappingService;
        this.templateEngines = templateEngines;
        this.templateConversionService = templateConversionService;
        this.objectMapper = objectMapper;
    }

    public GeneratedDocumentMetadata generate(GenerateDocumentRequest request) {
        PreparedGeneration prepared = prepareGeneration(request, true);

        TemplateEngine engine = templateEngines.stream()
                .filter(it -> it.supports(prepared.template().type()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe motor para tipo " + prepared.template().type()));

        Map<String, byte[]> signatures = decodeSignatures(prepared.signaturesBase64());

        GeneratedFile generatedFile = engine.generate(
                Path.of(prepared.template().filePath()),
                prepared.fields(),
                signatures);

        byte[] outputContent = generatedFile.content();
        if (prepared.template().type() == TemplateType.DOCX && prepared.outputType() == TemplateType.PDF) {
            outputContent = templateConversionService.convertDocxToPdf(generatedFile.content());
        }

        return fileStorageService.storeGeneratedDocument(
                prepared.template().id(),
                prepared.outputType(),
                outputContent);
    }

    public GenerateDocumentPreviewResponse preview(GenerateDocumentRequest request) {
        PreparedGeneration prepared = prepareGeneration(request, false);
        List<String> warnings = new ArrayList<>(prepared.warnings());
        boolean signaturesValid = true;
        try {
            decodeSignatures(prepared.signaturesBase64());
        } catch (BadRequestException ex) {
            signaturesValid = false;
            warnings.add("Hay firmas con formato invalido: " + ex.getMessage());
        }

        return new GenerateDocumentPreviewResponse(
                prepared.template().id(),
                prepared.template().type(),
                prepared.outputType(),
                prepared.requiredFields(),
                prepared.requiredSignatures(),
                prepared.fields().keySet(),
                prepared.signaturesBase64().keySet(),
                prepared.missingFields(),
                prepared.missingSignatures(),
                warnings,
                prepared.missingFields().isEmpty() && prepared.missingSignatures().isEmpty() && signaturesValid);
    }

    private PreparedGeneration prepareGeneration(GenerateDocumentRequest request, boolean validateStrictly) {
        TemplateMetadata template = fileStorageService.loadTemplate(request.getTemplateId());
        TemplateType outputType = resolveOutputType(request, template.type());

        Map<String, String> requestFields = normalizeStringMap(request.fieldsOrEmpty());
        Map<String, String> requestSignatures = normalizeStringMap(request.signaturesOrEmpty());
        validateRequestShape(request, requestFields, requestSignatures);
        ParsedDataJson parsedDataJson = parseDataJson(request);
        TemplateMappingMetadata mapping = templateMappingService.loadOrEmpty(template.id());

        Map<String, String> fields = buildFields(request, parsedDataJson, mapping, requestFields);
        Map<String, String> signaturesBase64 = buildSignatures(request, parsedDataJson, mapping, requestSignatures);

        Set<String> templateFieldKeys = templateRequirementsService.extractRequiredFields(template);
        Set<String> templateSignatureKeys = templateRequirementsService.extractRequiredSignatures(template);
        Set<String> requiredFields = resolveRequiredKeys(
                normalizeStringSet(request.requiredFieldKeysOrEmpty()),
                normalizeStringSet(mapping.requiredFieldKeys()),
                templateFieldKeys);
        Set<String> requiredSignatures = resolveRequiredKeys(
                normalizeStringSet(request.requiredSignatureKeysOrEmpty()),
                normalizeStringSet(mapping.requiredSignatureKeys()),
                templateSignatureKeys);

        applySignatureFallbacks(signaturesBase64, requiredSignatures);

        Set<String> missingFields = findMissing(requiredFields, fields.keySet());
        Set<String> missingSignatures = findMissing(requiredSignatures, signaturesBase64.keySet());

        if (validateStrictly) {
            validateMissing("Faltan campos requeridos", missingFields);
            validateMissing(
                    "Faltan firmas requeridas. Sube cada firma o envia alumnos[].firma/base64 en dataJson",
                    missingSignatures);
            decodeSignatures(signaturesBase64);
        }

        return new PreparedGeneration(
                template,
                outputType,
                fields,
                signaturesBase64,
                requiredFields,
                requiredSignatures,
                missingFields,
                missingSignatures,
                collectWarnings(template.type(), outputType, fields, signaturesBase64));
    }

    private void validateRequestShape(
            GenerateDocumentRequest request,
            Map<String, String> requestFields,
            Map<String, String> requestSignatures) {
        if (requestFields.size() > MAX_FIELDS) {
            throw new BadRequestException("La peticion supera el maximo de campos permitidos");
        }
        if (requestSignatures.size() > MAX_SIGNATURES) {
            throw new BadRequestException("La peticion supera el maximo de firmas permitidas");
        }
        if (request.getDataJson() != null && request.getDataJson().length() > MAX_DATA_JSON_CHARS) {
            throw new BadRequestException("dataJson supera el tamano maximo permitido");
        }
    }

    private List<String> collectWarnings(
            TemplateType templateType,
            TemplateType outputType,
            Map<String, String> fields,
            Map<String, String> signaturesBase64) {
        List<String> warnings = new ArrayList<>();
        if (templateType == TemplateType.DOCX && outputType == TemplateType.PDF) {
            warnings.add("Se aplicara conversion DOCX->PDF al finalizar la generacion");
        }
        if (fields.isEmpty()) {
            warnings.add("No se detectaron campos de texto para completar");
        }
        if (signaturesBase64.isEmpty()) {
            warnings.add("No se incluyeron firmas para el documento");
        }
        return warnings;
    }

    private Set<String> findMissing(Set<String> required, Set<String> providedFieldKeys) {
        if (required.isEmpty()) {
            return Set.of();
        }
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(providedFieldKeys);
        return missing;
    }

    private void validateMissing(String messagePrefix, Set<String> missing) {
        if (!missing.isEmpty()) {
            throw new BadRequestException(messagePrefix + ": " + String.join(", ", missing));
        }
    }

    private TemplateType resolveOutputType(GenerateDocumentRequest request, TemplateType templateType) {
        if (request.getOutputType() == null || request.getOutputType().isBlank()) {
            return templateType;
        }
        TemplateType desired = TemplateType.fromValue(request.getOutputType());
        if (templateType == TemplateType.PDF && desired != TemplateType.PDF) {
            throw new BadRequestException("No se puede generar DOCX a partir de una plantilla PDF");
        }
        return desired;
    }

    private Map<String, String> buildFields(
            GenerateDocumentRequest request,
            ParsedDataJson parsedDataJson,
            TemplateMappingMetadata mapping,
            Map<String, String> requestFields) {
        Map<String, String> fields = new LinkedHashMap<>();

        if (request.isForceJson()) {
            fields.putAll(requestFields);
            fields.putAll(parsedDataJson.fields());
            applyMappedValues(mapping.fieldMappings(), parsedDataJson.fields(), fields);
        } else {
            fields.putAll(parsedDataJson.fields());
            applyMappedValues(mapping.fieldMappings(), parsedDataJson.fields(), fields);
            fields.putAll(requestFields);
        }

        return fields;
    }

    private Map<String, String> buildSignatures(
            GenerateDocumentRequest request,
            ParsedDataJson parsedDataJson,
            TemplateMappingMetadata mapping,
            Map<String, String> requestSignatures) {
        Map<String, String> signatures = new LinkedHashMap<>();

        if (request.isForceJson()) {
            signatures.putAll(requestSignatures);
            signatures.putAll(parsedDataJson.signatures());
            applyMappedValues(mapping.signatureMappings(), parsedDataJson.signatures(), signatures);
        } else {
            signatures.putAll(parsedDataJson.signatures());
            applyMappedValues(mapping.signatureMappings(), parsedDataJson.signatures(), signatures);
            signatures.putAll(requestSignatures);
        }

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
            String value = sourceValues.get(sourceCode.toUpperCase(Locale.ROOT));
            if (value != null && !value.isBlank()) {
                targetValues.put(templateVariable.trim(), value);
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
                target.put(prefix.toUpperCase(Locale.ROOT), node.asText(""));
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
            JsonNode upperNode = root.get(name.toUpperCase(Locale.ROOT));
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
                .collect(Collectors.joining(", "));
    }

    private String findText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null) {
                value = node.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null) {
                value = node.get(key.toUpperCase(Locale.ROOT));
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

    private Set<String> resolveRequiredKeys(
            Set<String> requestRequired,
            Set<String> mappingRequired,
            Set<String> availableKeys) {
        Set<String> preferredSource = !requestRequired.isEmpty() ? requestRequired : mappingRequired;
        if (preferredSource.isEmpty()) {
            return Set.of();
        }
        Set<String> resolved = new LinkedHashSet<>(preferredSource);
        resolved.retainAll(new HashSet<>(availableKeys));
        return resolved;
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
            if (!isPngOrJpeg(bytes)) {
                throw new BadRequestException("La firma debe ser PNG o JPEG");
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Firma con formato base64 invalido");
        }
    }

    private boolean isPngOrJpeg(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        boolean png = (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47;
        boolean jpeg = (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
        return png || jpeg;
    }

    private Map<String, String> normalizeStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isBlank()) {
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }

    private Set<String> normalizeStringSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return source.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record ParsedDataJson(Map<String, String> fields, Map<String, String> signatures) {
        private static ParsedDataJson empty() {
            return new ParsedDataJson(Map.of(), Map.of());
        }
    }

    private record PreparedGeneration(
            TemplateMetadata template,
            TemplateType outputType,
            Map<String, String> fields,
            Map<String, String> signaturesBase64,
            Set<String> requiredFields,
            Set<String> requiredSignatures,
            Set<String> missingFields,
            Set<String> missingSignatures,
            List<String> warnings) {
    }
}
