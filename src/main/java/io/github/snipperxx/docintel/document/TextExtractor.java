package io.github.snipperxx.docintel.document;

import io.github.snipperxx.docintel.common.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Turns an uploaded file into plain text. Line endings are normalized so that the character
 * offsets stored with each fact are stable no matter which OS produced the file.
 */
@Component
public class TextExtractor {

    public static final int MAX_CHARS = 1_000_000;

    public ExtractedText extract(String filename, String declaredContentType, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = declaredContentType == null ? "" : declaredContentType.toLowerCase(Locale.ROOT);

        // A file extension wins over the declared type, which browsers and clients often guess wrong.
        String format = extension(name);
        if (format.isEmpty()) {
            format = switch (type) {
                case "application/pdf" -> "pdf";
                case "text/markdown" -> "md";
                case "text/plain" -> "txt";
                default -> type.startsWith("text/plain;") ? "txt" : "";
            };
        }
        ExtractedText result = switch (format) {
            case "pdf" -> new ExtractedText("application/pdf", readPdf(bytes));
            case "md", "markdown" -> new ExtractedText("text/markdown", decodeUtf8(bytes));
            case "txt" -> new ExtractedText("text/plain", decodeUtf8(bytes));
            default -> throw new BadRequestException("Unsupported file type; upload .txt, .md or .pdf");
        };

        String text = normalize(result.text());
        if (text.isBlank()) {
            throw new BadRequestException("The file contains no extractable text");
        }
        if (text.length() > MAX_CHARS) {
            throw new BadRequestException("Document text exceeds " + MAX_CHARS + " characters");
        }
        return new ExtractedText(result.contentType(), text);
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1);
    }

    private static String readPdf(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(pdf);
        } catch (IOException e) {
            throw new BadRequestException("Could not read PDF: " + e.getMessage());
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new BadRequestException("Text files must be UTF-8 encoded");
        }
    }

    static String normalize(String text) {
        String withoutBom = text.startsWith("﻿") ? text.substring(1) : text;
        return withoutBom.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    public record ExtractedText(String contentType, String text) {
    }
}
