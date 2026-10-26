package com.andersonlopes.caddocbridge.utils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class DocxToPdfConverter {

    public static File convertDocxToPdf(File docxFile, File outputPdfFile) throws Exception {
        InputStream inputStream = new FileInputStream(docxFile);
        XWPFDocument document = new XWPFDocument(inputStream);

        PdfDocument pdfDocument = new PdfDocument();
        // Formato A4 padrão (595 x 842 pixels/pontos)
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(12f);

        int x = 40;
        int y = 50;
        int lineHeight = 20;

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();
            if (text != null && !text.isEmpty()) {
                // Desenha o texto linha a linha na página do PDF
                canvas.drawText(text, x, y, paint);
                y += lineHeight;

                // Se o texto atingir o fundo da página A4, cria uma nova página
                if (y > 800) {
                    pdfDocument.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pdfDocument.getPages().size() + 1).create();
                    page = pdfDocument.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 50; // Reinicia o topo da nova página
                }
            }
        }

        pdfDocument.finishPage(page);

        FileOutputStream fos = new FileOutputStream(outputPdfFile);
        pdfDocument.writeTo(fos);

        // Fechar fluxos
        document.close();
        pdfDocument.close();
        inputStream.close();
        fos.close();

        return outputPdfFile;
    }
}