package com.andersonlopes.caddocbridge.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gera um .docx mínimo (um parágrafo por linha de texto) sem depender do Apache POI.
 * Não fecha o OutputStream recebido; quem chamou continua dono dele.
 */
public final class DocxWriter {

    private static final String CONTENT_TYPES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>";

    private static final String RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>";

    private DocxWriter() {
    }

    public static void write(String text, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        putEntry(zip, "[Content_Types].xml", CONTENT_TYPES);
        putEntry(zip, "_rels/.rels", RELS);
        putEntry(zip, "word/document.xml", buildDocument(text));
        zip.finish(); // grava o diretório central sem fechar "out"
        out.flush();
    }

    private static String buildDocument(String text) {
        StringBuilder xml = new StringBuilder(text.length() * 2 + 512);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");

        for (String line : text.split("\\r?\\n", -1)) {
            String clean = escape(line);
            if (clean.isEmpty()) {
                xml.append("<w:p/>");
            } else {
                xml.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(clean).append("</w:t></w:r></w:p>");
            }
        }

        // Página A4 com margens de 2,54 cm
        xml.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
                .append("<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>")
                .append("</w:sectPr></w:body></w:document>");
        return xml.toString();
    }

    /** Escapa &, <, > e descarta caracteres de controle inválidos em XML 1.0. */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else if (c == '\t' || c >= 0x20) sb.append(c);
            // demais controles (0x00-0x1F) são descartados
        }
        return sb.toString();
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
