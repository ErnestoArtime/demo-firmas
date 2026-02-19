package com.example.demo.service.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.springframework.stereotype.Component;

import com.example.demo.exception.BadRequestException;
import com.example.demo.model.TemplateType;

@Component
public class PdfTemplateEngine implements TemplateEngine {

    @Override
    public boolean supports(TemplateType type) {
        return type == TemplateType.PDF;
    }

    @Override
    public GeneratedFile generate(Path templatePath, Map<String, String> fields, Map<String, byte[]> signatures) {
        try (PDDocument document = PDDocument.load(templatePath.toFile());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                throw new BadRequestException("El PDF no contiene campos de formulario (AcroForm)");
            }

            fillTextFields(acroForm, fields);
            drawSignatureImages(document, acroForm, signatures);

            acroForm.flatten();
            document.save(out);
            return new GeneratedFile(TemplateType.PDF, out.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Error generando PDF desde plantilla", ex);
        }
    }

    private void fillTextFields(PDAcroForm acroForm, Map<String, String> fields) throws IOException {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            PDField field = acroForm.getField(entry.getKey());
            if (field != null) {
                field.setValue(entry.getValue() == null ? "" : entry.getValue());
            }
        }
    }

    private void drawSignatureImages(
            PDDocument document,
            PDAcroForm acroForm,
            Map<String, byte[]> signatures) throws IOException {
        for (Map.Entry<String, byte[]> signature : signatures.entrySet()) {
            PDField field = acroForm.getField(signature.getKey());
            if (!(field instanceof PDTerminalField terminalField)) {
                continue;
            }

            PDImageXObject image = PDImageXObject.createFromByteArray(
                    document, signature.getValue(), "firma-" + signature.getKey());

            for (PDAnnotationWidget widget : terminalField.getWidgets()) {
                PDPage page = findWidgetPage(document, widget);
                if (page == null) {
                    continue;
                }
                PDRectangle rect = widget.getRectangle();
                if (rect == null) {
                    continue;
                }
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, AppendMode.APPEND, true, true)) {
                    contentStream.drawImage(
                            image,
                            rect.getLowerLeftX(),
                            rect.getLowerLeftY(),
                            rect.getWidth(),
                            rect.getHeight());
                }
            }
        }
    }

    private PDPage findWidgetPage(PDDocument document, PDAnnotationWidget widget) throws IOException {
        if (widget.getPage() != null) {
            return widget.getPage();
        }
        for (PDPage page : document.getPages()) {
            if (page.getAnnotations().contains(widget)) {
                return page;
            }
        }
        return null;
    }
}
