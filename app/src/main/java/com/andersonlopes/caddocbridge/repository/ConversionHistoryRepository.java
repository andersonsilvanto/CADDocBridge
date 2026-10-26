package com.andersonlopes.caddocbridge.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.andersonlopes.caddocbridge.data.room.AppDatabase;
import com.andersonlopes.caddocbridge.data.room.ConversionHistoryDao;
import com.andersonlopes.caddocbridge.model.ConversionHistory;
import com.andersonlopes.caddocbridge.utils.FileStorageManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ConversionHistoryRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(@NonNull Exception error);
    }

    public static final class DeleteSummary {
        public final int removedRecords;
        public final int deletedFiles;
        public final int failedFiles;
        public final long freedBytes;

        DeleteSummary(int removedRecords, int deletedFiles, int failedFiles, long freedBytes) {
            this.removedRecords = removedRecords;
            this.deletedFiles = deletedFiles;
            this.failedFiles = failedFiles;
            this.freedBytes = freedBytes;
        }
    }

    private static final int SQL_CHUNK_SIZE = 500;
    private final ConversionHistoryDao dao;
    private final FileStorageManager storage;
    private final ExecutorService executor = AppDatabase.DB_EXECUTOR;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ConversionHistoryRepository(@NonNull Context context) {
        Context app = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(app).conversionHistoryDao();
        this.storage = new FileStorageManager(app);
    }

    @NonNull
    public FileStorageManager getStorage() {
        return storage;
    }

    @NonNull
    public LiveData<List<ConversionHistory>> observeHistory(@Nullable String query) {
        String q = query == null ? "" : query.trim();
        return q.isEmpty() ? dao.observeAll() : dao.search(q);
    }

    public void recordConversion(@NonNull final File file, @Nullable final Callback<Long> callback) {
        executor.execute(() -> {
            try {
                if (!file.exists()) {
                    throw new IOException("Ficheiro não encontrado: " + file.getAbsolutePath());
                }
                String ext = FileStorageManager.getExtensionUpper(file.getName());
                String format = file.isDirectory() ? "PASTA" : (ext.isEmpty() ? "FICHEIRO" : ext);
                long size = FileStorageManager.sizeOf(file);

                ConversionHistory entry = new ConversionHistory(
                        file.getName(), file.getAbsolutePath(), format, size);
                long id = dao.insert(entry);
                postSuccess(callback, id);
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void deleteEntries(@NonNull final List<ConversionHistory> entries,
                              final boolean deleteFiles,
                              @Nullable final Callback<DeleteSummary> callback) {
        executor.execute(() -> {
            try {
                List<Long> ids = new ArrayList<>();
                List<File> files = new ArrayList<>();
                for (ConversionHistory entry : entries) {
                    ids.add(entry.getId());
                    if (deleteFiles && entry.getFilePath() != null) {
                        File file = new File(entry.getFilePath());
                        if (file.exists()) {
                            files.add(file);
                        }
                    }
                }

                int deletedFiles = 0;
                int failedFiles = 0;
                long freed = 0L;
                if (!files.isEmpty()) {
                    FileStorageManager.PurgeResult result = storage.purgeBatch(files);
                    deletedFiles = result.deletedCount;
                    failedFiles = result.failedCount;
                    freed = result.freedBytes;
                }

                int removed = deleteIdsChunked(ids);
                postSuccess(callback, new DeleteSummary(removed, deletedFiles, failedFiles, freed));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void clearAll(final boolean deleteFiles, @Nullable final Callback<DeleteSummary> callback) {
        executor.execute(() -> {
            try {
                int deletedFiles = 0;
                int failedFiles = 0;
                long freed = 0L;
                if (deleteFiles) {
                    FileStorageManager.PurgeResult result = storage.purgeAll();
                    deletedFiles = result.deletedCount;
                    failedFiles = result.failedCount;
                    freed = result.freedBytes;
                }
                int removed = dao.deleteAll();
                postSuccess(callback, new DeleteSummary(removed, deletedFiles, failedFiles, freed));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void syncWithDisk(@Nullable final Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                List<Long> orphanIds = new ArrayList<>();
                for (ConversionHistory entry : dao.getAllSync()) {
                    String path = entry.getFilePath();
                    if (path == null || !new File(path).exists()) {
                        orphanIds.add(entry.getId());
                    }
                }
                postSuccess(callback, deleteIdsChunked(orphanIds));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void calculateStorageSize(@Nullable final Callback<Long> callback) {
        executor.execute(() -> {
            try {
                postSuccess(callback, storage.getTotalSize());
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    private int deleteIdsChunked(@NonNull List<Long> ids) {
        int total = 0;
        for (int i = 0; i < ids.size(); i += SQL_CHUNK_SIZE) {
            int end = Math.min(i + SQL_CHUNK_SIZE, ids.size());
            total += dao.deleteByIds(new ArrayList<>(ids.subList(i, end)));
        }
        return total;
    }

    private <T> void postSuccess(@Nullable final Callback<T> callback, final T result) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(@Nullable final Callback<T> callback, @NonNull final Exception e) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onError(e));
    }
}