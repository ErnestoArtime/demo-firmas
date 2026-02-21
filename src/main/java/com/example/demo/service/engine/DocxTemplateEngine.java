package com.example.demo.service.engine;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import jakarta.xml.bind.JAXBElement;

import org.docx4j.XmlUtils;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.jaxb.Context;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.springframework.stereotype.Component;

import com.example.demo.model.TemplateType;

@Component
public class DocxTemplateEngine implements TemplateEngine {

    private static final ObjectFactory WML_OBJECT_FACTORY = Context.getWmlObjectFactory();
    private static final int PX_TO_EMU = 9525;
    private static final long SIGNATURE_WIDTH_EMU = 220L * PX_TO_EMU;
    private static final long SIGNATURE_HEIGHT_EMU = 70L * PX_TO_EMU;
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("(\\$\\{([A-Za-z0-9_]+)})|\\b([A-Za-z][A-Za-z0-9_]*)#");
    private static final Pattern STUDENT_ROW_DOCX_PATTERN =
            Pattern.compile("\\$\\{ALUMNO_1_[A-Za-z0-9_]+}|\\$\\{FIRMA_?1}|\\bALUMNO_1_[A-Za-z0-9_]+#|\\bFIRMA_?1#");
    private static final Pattern STUDENT_INDEXED_TOKEN_PATTERN =
            Pattern.compile("ALUMNO_(\\d+)_|FIRMA_?(\\d+)");

    @Override
    public boolean supports(TemplateType type) {
        return type == TemplateType.DOCX;
    }

    @Override
    public GeneratedFile generate(Path templatePath, Map<String, String> fields, Map<String, byte[]> signatures) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            WordprocessingMLPackage word = WordprocessingMLPackage.load(Files.newInputStream(templatePath));
            VariablePrepare.prepare(word);

            duplicateStudentRows(word, fields, signatures);
            replaceHashFieldTokens(word, fields);
            replaceSignatures(word, signatures);
            word.getMainDocumentPart().variableReplace(fields);

            word.save(out);
            return new GeneratedFile(TemplateType.DOCX, out.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Error generando DOCX desde plantilla", ex);
        }
    }

    private void replaceSignatures(WordprocessingMLPackage word, Map<String, byte[]> signatures) throws Exception {
        if (signatures.isEmpty()) {
            return;
        }

        List<Object> textNodes = getAllElementsFromObject(word.getMainDocumentPart(), Text.class);
        AtomicInteger imageCounter = new AtomicInteger(1);

        for (Object obj : textNodes) {
            Text text = (Text) obj;
            String value = text.getValue();
            if (value == null) {
                continue;
            }
            R run = findParentRun(text);
            if (run == null) {
                continue;
            }
            List<Object> replacementContent = buildReplacementContent(
                    word, value, signatures, imageCounter);
            if (replacementContent.isEmpty()) {
                continue;
            }
            run.getContent().clear();
            run.getContent().addAll(replacementContent);
        }
    }

    private void duplicateStudentRows(
            WordprocessingMLPackage word,
            Map<String, String> fields,
            Map<String, byte[]> signatures) {
        int maxStudentIndex = resolveMaxStudentIndex(fields, signatures);
        if (maxStudentIndex <= 1) {
            return;
        }

        List<Object> rows = getAllElementsFromObject(word.getMainDocumentPart(), Tr.class);
        for (Object rowObj : rows) {
            Tr row = (Tr) rowObj;
            String rowText = extractRowText(row);
            if (rowText.isBlank() || !STUDENT_ROW_DOCX_PATTERN.matcher(rowText).find()) {
                continue;
            }

            if (!(XmlUtils.unwrap(row.getParent()) instanceof org.docx4j.wml.ContentAccessor parent)) {
                continue;
            }
            List<Object> parentContent = parent.getContent();
            int rowIndex = parentContent.indexOf(row);
            if (rowIndex < 0) {
                continue;
            }

            for (int studentIndex = 2; studentIndex <= maxStudentIndex; studentIndex++) {
                Tr clonedRow = (Tr) XmlUtils.deepCopy(row);
                replaceStudentIndexTokens(clonedRow, studentIndex);
                parentContent.add(rowIndex + (studentIndex - 1), clonedRow);
            }
        }
    }

    private int resolveMaxStudentIndex(Map<String, String> fields, Map<String, byte[]> signatures) {
        int maxFromFields = fields.keySet().stream()
                .mapToInt(this::extractStudentIndex)
                .max()
                .orElse(0);
        int maxFromSignatures = signatures.keySet().stream()
                .mapToInt(this::extractStudentIndex)
                .max()
                .orElse(0);
        return Math.max(maxFromFields, maxFromSignatures);
    }

    private int extractStudentIndex(String key) {
        Matcher matcher = STUDENT_INDEXED_TOKEN_PATTERN.matcher(key);
        if (!matcher.find()) {
            return 0;
        }
        String group = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (group == null || group.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(group);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String extractRowText(Tr row) {
        List<Object> textNodes = getAllElementsFromObject(row, Text.class);
        StringBuilder builder = new StringBuilder();
        for (Object obj : textNodes) {
            Text text = (Text) obj;
            if (text.getValue() != null) {
                builder.append(text.getValue()).append(' ');
            }
        }
        return builder.toString();
    }

    private void replaceStudentIndexTokens(Tr row, int studentIndex) {
        List<Object> textNodes = getAllElementsFromObject(row, Text.class);
        for (Object obj : textNodes) {
            Text text = (Text) obj;
            String value = text.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }

            String updated = value.replace("ALUMNO_1_", "ALUMNO_" + studentIndex + "_")
                    .replace("FIRMA_1", "FIRMA_" + studentIndex)
                    .replace("FIRMA1", "FIRMA" + studentIndex);
            text.setValue(updated);
        }
    }

    private void replaceHashFieldTokens(WordprocessingMLPackage word, Map<String, String> fields) {
        if (fields.isEmpty()) {
            return;
        }
        List<Object> textNodes = getAllElementsFromObject(word.getMainDocumentPart(), Text.class);
        for (Object obj : textNodes) {
            Text text = (Text) obj;
            String value = text.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            String updated = value;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                String token = entry.getKey() + "#";
                String replacement = entry.getValue() == null ? "" : entry.getValue();
                updated = updated.replace(token, replacement);
            }
            if (!updated.equals(value)) {
                text.setValue(updated);
            }
        }
    }

    private List<Object> buildReplacementContent(
            WordprocessingMLPackage word,
            String originalValue,
            Map<String, byte[]> signatures,
            AtomicInteger imageCounter) throws Exception {
        List<Object> content = new ArrayList<>();
        boolean replacedSomething = false;

        Matcher matcher = TOKEN_PATTERN.matcher(originalValue);
        int cursor = 0;
        while (matcher.find()) {
            addTextIfNotEmpty(content, originalValue.substring(cursor, matcher.start()));
            String key = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            byte[] signature = signatures.get(key);
            if (signature == null) {
                addTextIfNotEmpty(content, matcher.group(0));
            } else {
                content.add(createDrawing(word, key, signature, imageCounter.getAndIncrement()));
                replacedSomething = true;
            }
            cursor = matcher.end();
        }
        addTextIfNotEmpty(content, originalValue.substring(cursor));

        return replacedSomething ? content : List.of();
    }

    private void addTextIfNotEmpty(List<Object> content, String textValue) {
        if (textValue == null || textValue.isEmpty()) {
            return;
        }
        Text text = WML_OBJECT_FACTORY.createText();
        text.setValue(textValue);
        content.add(text);
    }

    private Drawing createDrawing(
            WordprocessingMLPackage word,
            String key,
            byte[] imageBytes,
            int imageNumber) throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(word, imageBytes);
        Inline inline = imagePart.createImageInline(
                "firma-" + key,
                "firma-" + key,
                imageNumber,
                imageNumber + 10_000,
                SIGNATURE_WIDTH_EMU,
                SIGNATURE_HEIGHT_EMU,
                false);

        Drawing drawing = WML_OBJECT_FACTORY.createDrawing();
        drawing.getAnchorOrInline().add(inline);
        return drawing;
    }

    private R findParentRun(Text text) {
        Object parent = XmlUtils.unwrap(text.getParent());
        if (parent instanceof R run) {
            return run;
        }
        return null;
    }

    private List<Object> getAllElementsFromObject(Object obj, Class<?> targetClass) {
        List<Object> result = new java.util.ArrayList<>();
        collect(obj, targetClass, result);
        return result;
    }

    private void collect(Object obj, Class<?> targetClass, List<Object> result) {
        if (obj == null) {
            return;
        }
        Object unwrapped = XmlUtils.unwrap(obj);
        if (targetClass.isAssignableFrom(unwrapped.getClass())) {
            result.add(unwrapped);
        }
        if (unwrapped instanceof JAXBElement<?> element) {
            collect(element.getValue(), targetClass, result);
            return;
        }
        if (unwrapped instanceof ContentAccessor accessor) {
            for (Object child : accessor.getContent()) {
                collect(child, targetClass, result);
            }
        }
    }
}
