package com.example.demo.service.engine;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.stereotype.Component;

import com.example.demo.model.TemplateType;

@Component
public class DocxTemplateEngine implements TemplateEngine {

    private static final ObjectFactory WML_OBJECT_FACTORY = Context.getWmlObjectFactory();
    private static final int PX_TO_EMU = 9525;
    private static final long SIGNATURE_WIDTH_EMU = 220L * PX_TO_EMU;
    private static final long SIGNATURE_HEIGHT_EMU = 70L * PX_TO_EMU;

    @Override
    public boolean supports(TemplateType type) {
        return type == TemplateType.DOCX;
    }

    @Override
    public GeneratedFile generate(Path templatePath, Map<String, String> fields, Map<String, byte[]> signatures) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            WordprocessingMLPackage word = WordprocessingMLPackage.load(Files.newInputStream(templatePath));
            VariablePrepare.prepare(word);

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

    private List<Object> buildReplacementContent(
            WordprocessingMLPackage word,
            String originalValue,
            Map<String, byte[]> signatures,
            AtomicInteger imageCounter) throws Exception {
        List<Object> content = new ArrayList<>();
        boolean replacedSomething = false;

        int cursor = 0;
        while (cursor < originalValue.length()) {
            int start = originalValue.indexOf("${", cursor);
            if (start < 0) {
                addTextIfNotEmpty(content, originalValue.substring(cursor));
                break;
            }
            int end = originalValue.indexOf('}', start + 2);
            if (end < 0) {
                addTextIfNotEmpty(content, originalValue.substring(cursor));
                break;
            }

            addTextIfNotEmpty(content, originalValue.substring(cursor, start));

            String key = originalValue.substring(start + 2, end);
            byte[] signature = signatures.get(key);
            if (signature != null) {
                content.add(createDrawing(word, key, signature, imageCounter.getAndIncrement()));
                replacedSomething = true;
            } else {
                addTextIfNotEmpty(content, originalValue.substring(start, end + 1));
            }
            cursor = end + 1;
        }

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
