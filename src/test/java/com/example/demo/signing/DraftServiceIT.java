package com.example.demo.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.exception.BadRequestException;
import com.example.demo.model.TemplateMetadata;
import com.example.demo.service.FileStorageService;
import com.example.demo.signing.dto.CreateDraftRequest;
import com.example.demo.signing.dto.DraftSignerInput;
import com.example.demo.signing.dto.DraftSignerView;
import com.example.demo.signing.dto.DraftStatusResponse;
import com.example.demo.signing.dto.FinalizeDraftResponse;
import com.example.demo.signing.dto.SignByTokenInfo;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:draft-it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.templates-dir=target/test-storage/templates",
        "app.storage.output-dir=target/test-storage/output",
        "app.drafts.public-base-url=http://test"
})
@DirtiesContext
class DraftServiceIT {

    private static final byte[] PNG_SAMPLE = buildSamplePng();

    private static byte[] buildSamplePng() {
        try {
            BufferedImage img = new BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, 160, 80);
            g.setColor(Color.BLACK);
            g.drawLine(10, 40, 150, 40);
            g.drawLine(20, 20, 140, 60);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear PNG de prueba", ex);
        }
    }

    @Autowired
    private DraftService draftService;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    @Transactional
    void draftFlowWorksEndToEnd() throws IOException {
        TemplateMetadata template = uploadDocxTemplate();

        CreateDraftRequest req = new CreateDraftRequest();
        req.setTemplateId(template.id());
        req.setTitle("Acta test");
        req.setOutputType("docx");
        req.setSigners(List.of(
                signer("Ana Lopez", "12345678A"),
                signer("Luis Perez", "87654321B")));

        DraftStatusResponse created = draftService.createDraft(req, null);
        assertThat(created.status()).isEqualTo(DraftStatus.PENDIENTE);
        assertThat(created.signers()).hasSize(2);

        DraftSignerView slot0 = created.signers().get(0);
        DraftSignerView slot1 = created.signers().get(1);
        assertThat(slot0.shareUrl()).contains("/firmar/");
        assertThat(slot0.signatureKey()).isEqualTo("FIRMA_1");
        assertThat(slot1.signatureKey()).isEqualTo("FIRMA_2");

        String tokenA = extractToken(slot0.shareUrl());
        String tokenB = extractToken(slot1.shareUrl());
        String b64 = Base64.getEncoder().encodeToString(PNG_SAMPLE);

        SignByTokenInfo afterFirst = draftService.signByToken(tokenA, b64, null);
        assertThat(afterFirst.slotStatus()).isEqualTo(SignerSlotStatus.FIRMADO);
        assertThat(draftService.getDraft(created.draftId()).status()).isEqualTo(DraftStatus.PARCIAL);

        assertThatThrownBy(() -> draftService.signByToken(tokenA, b64, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue firmada");

        draftService.signByToken(tokenB, b64, null);
        DraftStatusResponse completo = draftService.getDraft(created.draftId());
        assertThat(completo.status()).isEqualTo(DraftStatus.COMPLETO);
        assertThat(completo.signedSigners()).isEqualTo(2);

        FinalizeDraftResponse finalized = draftService.finalizeDraft(created.draftId(), false, null);
        assertThat(finalized.status()).isEqualTo(DraftStatus.FINALIZADO);
        assertThat(finalized.generatedDocumentId()).isNotBlank();
        assertThat(finalized.downloadUrl()).isEqualTo("/api/documents/" + finalized.generatedDocumentId());

        assertThatThrownBy(() -> draftService.finalizeDraft(created.draftId(), false, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya fue finalizado");
    }

    @Test
    @Transactional
    void cancelDraftBlocksFutureSigning() throws IOException {
        TemplateMetadata template = uploadDocxTemplate();
        CreateDraftRequest req = new CreateDraftRequest();
        req.setTemplateId(template.id());
        req.setSigners(List.of(signer("X", "12345678X")));
        DraftStatusResponse created = draftService.createDraft(req, null);
        String token = extractToken(created.signers().get(0).shareUrl());

        draftService.cancelDraft(created.draftId(), null);

        assertThatThrownBy(() -> draftService.signByToken(
                token, Base64.getEncoder().encodeToString(PNG_SAMPLE), null))
                .isInstanceOf(BadRequestException.class);
    }

    private DraftSignerInput signer(String name, String nif) {
        DraftSignerInput s = new DraftSignerInput();
        s.setSignerName(name);
        s.setSignerNif(nif);
        return s;
    }

    private String extractToken(String shareUrl) {
        int idx = shareUrl.lastIndexOf('/');
        return shareUrl.substring(idx + 1);
    }

    private TemplateMetadata uploadDocxTemplate() throws IOException {
        byte[] docx = buildMinimalDocx();
        MockMultipartFile file = new MockMultipartFile("file", "plantilla.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        return fileStorageService.storeTemplate(file);
    }

    private byte[] buildMinimalDocx() throws IOException {
        Path tmp = Files.createTempFile("test", ".docx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>").getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>").getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>").getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                    + "<w:body>"
                    + "<w:p><w:r><w:t>Acta de prueba. Firmas: ${FIRMA_1} ${FIRMA_2}</w:t></w:r></w:p>"
                    + "<w:sectPr/></w:body></w:document>").getBytes());
            zip.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(tmp);
        Files.deleteIfExists(tmp);
        return bytes;
    }
}
