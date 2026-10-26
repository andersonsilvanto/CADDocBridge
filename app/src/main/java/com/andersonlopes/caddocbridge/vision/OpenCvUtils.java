package com.andersonlopes.caddocbridge.vision;

import android.graphics.Bitmap;

public class OpenCvUtils {

    public static Bitmap processDocument(Bitmap src) {
        if (src == null) return null;
        return src;
    }

    public static Bitmap processDocumentFilter(Bitmap src) {
        if (src == null) return null;
        // Podes adicionar o filtro ou retornar o bitmap original por enquanto
        return src;
    }
}