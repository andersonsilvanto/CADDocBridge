package com.andersonlopes.caddocbridge.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FileStorageManager {

    public static final String DIR_NAME = "CadDocBridge_Files";
    private final File baseDir;

    public static final class PurgeResult {
        public final int deletedCount;
        public final int failedCount;
        public final long freedBytes;
        @NonNull
        public final List<String> deletedPaths;

        PurgeResult(int deletedCount, int failedCount, long freedBytes, @NonNull List<String> deletedPaths) {
            this.deletedCount = deletedCount;
            this.failedCount = failedCount;
            this.freedBytes = freedBytes;
            this.deletedPaths = deletedPaths;
        }
    }

    public FileStorageManager(@NonNull Context context) {
        // Mesma raiz que o MainActivity (getOutputFolder) e o file_paths.xml já usam:
        // getExternalFilesDir(null)/CadDocBridge_Files
        File root = context.getApplicationContext().getExternalFilesDir(null);
        if (root == null) {
            root = context.getApplicationContext().getFilesDir();
        }
        this.baseDir = new File(root, DIR_NAME);
        ensureDirectory();
    }

    public boolean ensureDirectory() {
        if (baseDir.exists()) {
            return baseDir.isDirectory();
        }
        return baseDir.mkdirs();
    }

    @NonNull
    public File getBaseDir() {
        return baseDir;
    }

    @NonNull
    public File createOutputFile(@NonNull String desiredName) throws IOException {
        if (!ensureDirectory()) {
            throw new IOException("Não foi possível criar o diretório: " + baseDir.getAbsolutePath());
        }
        String safeName = sanitizeFileName(desiredName);
        String base = safeName;
        String ext = "";
        int dot = safeName.lastIndexOf('.');
        if (dot > 0) {
            base = safeName.substring(0, dot);
            ext = safeName.substring(dot);
        }

        File candidate = new File(baseDir, safeName);
        int counter = 1;
        while (candidate.exists()) {
            candidate = new File(baseDir, base + " (" + counter + ")" + ext);
            counter++;
        }
        return candidate;
    }

    @NonNull
    public List<File> listFiles() {
        File[] files = baseDir.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> result = new ArrayList<>(Arrays.asList(files));
        Collections.sort(result, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    public long getTotalSize() {
        return sizeOf(baseDir);
    }

    @NonNull
    public PurgeResult purgeAll() {
        return purgeBatch(listFiles());
    }

    @NonNull
    public PurgeResult purgeBatch(@Nullable Collection<File> files) {
        int deleted = 0;
        int failed = 0;
        long freed = 0L;
        List<String> deletedPaths = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                if (file == null || !isInsideBaseDir(file) || !file.exists()) {
                    failed++;
                    continue;
                }
                long size = sizeOf(file);
                String path = file.getAbsolutePath();
                if (deleteRecursively(file)) {
                    deleted++;
                    freed += size;
                    deletedPaths.add(path);
                } else {
                    failed++;
                }
            }
        }
        return new PurgeResult(deleted, failed, freed, deletedPaths);
    }

    @NonNull
    public static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unit]);
    }

    @NonNull
    public static String getExtensionUpper(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    @NonNull
    private static String sanitizeFileName(@NonNull String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (cleaned.isEmpty() || cleaned.matches("\\.+")) {
            cleaned = "arquivo_" + System.currentTimeMillis();
        }
        return cleaned;
    }

    private boolean isInsideBaseDir(@NonNull File file) {
        try {
            String basePath = baseDir.getCanonicalPath() + File.separator;
            String filePath = file.getCanonicalPath();
            return filePath.startsWith(basePath);
        } catch (IOException e) {
            return false;
        }
    }

    public static long sizeOf(@NonNull File file) {
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                total += sizeOf(child);
            }
        }
        return total;
    }

    private static boolean deleteRecursively(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }
}