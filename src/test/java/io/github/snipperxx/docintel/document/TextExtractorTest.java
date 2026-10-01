package io.github.snipperxx.docintel.document;

import io.github.snipperxx.docintel.common.BadRequestException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void normalizesLineEndingsAndBom() {
        byte[] bytes = "﻿line one\r\nline two\r\n".getBytes(StandardCharsets.UTF_8);

        TextExtractor.ExtractedText result = extractor.extract("notes.txt", "text/plain", bytes);

        assertThat(result.text()).isEqualTo("line one\nline two");
        assertThat(result.contentType()).isEqualTo("text/plain");
    }

    @Test
    void detectsMarkdownByExtension() {
        TextExtractor.ExtractedText result = extractor.extract("README.md", "application/octet-stream",
                "# Title".getBytes(StandardCharsets.UTF_8));

        assertThat(result.contentType()).isEqualTo("text/markdown");
    }

    @Test
    void readsTextFromPdf() throws IOException {
        TextExtractor.ExtractedText result = extractor.extract("contract.pdf", "application/pdf",
                pdfWithText("Payment is due within 14 days"));

        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.text()).contains("Payment is due within 14 days");
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThatThrownBy(() -> extractor.extract("image.png", "image/png", new byte[]{1, 2, 3}))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void extensionWinsOverDeclaredContentType() {
        assertThatThrownBy(() -> extractor.extract("image.png", "text/plain", new byte[]{1, 2, 3}))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void fallsBackToContentTypeWithoutExtension() {
        TextExtractor.ExtractedText result = extractor.extract("notes", "text/plain; charset=utf-8",
                "plain".getBytes(StandardCharsets.UTF_8));

        assertThat(result.contentType()).isEqualTo("text/plain");
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] latin1 = "Grüße".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> extractor.extract("notes.txt", "text/plain", latin1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void rejectsBlankDocuments() {
        assertThatThrownBy(() -> extractor.extract("empty.txt", "text/plain", " \n ".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BadRequestException.class);
    }

    private static byte[] pdfWithText(String text) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }
}
