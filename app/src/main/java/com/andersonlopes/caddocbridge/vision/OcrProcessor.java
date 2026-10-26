package com.andersonlopes.caddocbridge.vision;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class OcrProcessor {

    public interface OcrCallback {
        void onSuccess(String extractedText);
        void onFailure(@NonNull Exception e);
    }

    // Compatibilidade com o nome que o ViewModel está a chamar
    public static void extractText(Bitmap bitmap, OcrCallback callback) {
        recognizeText(bitmap, callback);
    }

    /** Versão síncrona (offline). NÃO chamar na main thread. */
    public static String recognizeTextBlocking(Bitmap bitmap) throws IOException {
        if (bitmap == null) {
            throw new IOException("Imagem inválida para OCR.");
        }
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)));
            return result.getText();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Falha no OCR: " + cause.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OCR interrompido.", e);
        } finally {
            recognizer.close();
        }
    }

    public static void recognizeText(Bitmap bitmap, OcrCallback callback) {
        if (bitmap == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Bitmap inválido para OCR."));
            }
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    if (callback != null) {
                        callback.onSuccess(visionText.getText());
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
    }
}