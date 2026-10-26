package com.andersonlopes.caddocbridge.utils;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Abre e compartilha arquivos de /CadDocBridge_Files/ via FileProvider.
 * Requer o provider declarado no AndroidManifest (authority: ${applicationId}.fileprovider)
 * e o res/xml/file_paths.xml.
 */
public final class FileShareHelper {

    private FileShareHelper() {
    }

    @NonNull
    private static Uri toUri(@NonNull Context context, @NonNull File file) {
        return FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", file);
    }

    /** Abre o arquivo em um app externo. Retorna false se nenhum app puder abri-lo. */
    public static boolean open(@NonNull Context context, @NonNull File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(toUri(context, file), getMimeType(file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Compartilha um ou mais arquivos (ignora pastas). Retorna false se nada foi compartilhado. */
    public static boolean share(@NonNull Context context, @NonNull List<File> files,
                                @NonNull String chooserTitle) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) {
                try {
                    uris.add(toUri(context, file));
                } catch (IllegalArgumentException ignored) {
                    // arquivo fora dos caminhos do FileProvider
                }
            }
        }
        if (uris.isEmpty()) {
            return false;
        }

        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType(getMimeType(files.get(0)));
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("*/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip = ClipData.newRawUri("", uris.get(0));
        for (int i = 1; i < uris.size(); i++) {
            clip.addItem(new ClipData.Item(uris.get(i)));
        }
        intent.setClipData(clip);

        try {
            Intent chooser = Intent.createChooser(intent, chooserTitle);
            if (!(context instanceof android.app.Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(chooser);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    @NonNull
    public static String getMimeType(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        switch (ext) {
            case "dxf":
                return "image/vnd.dxf";
            default:
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                return mime != null ? mime : "application/octet-stream";
        }
    }
}
