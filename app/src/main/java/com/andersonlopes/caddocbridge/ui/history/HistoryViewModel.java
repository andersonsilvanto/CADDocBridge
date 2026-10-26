package com.andersonlopes.caddocbridge.ui.history;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.andersonlopes.caddocbridge.R;
import com.andersonlopes.caddocbridge.model.ConversionHistory;
import com.andersonlopes.caddocbridge.repository.ConversionHistoryRepository;
import com.andersonlopes.caddocbridge.utils.Event;
import com.andersonlopes.caddocbridge.utils.FileStorageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HistoryViewModel extends AndroidViewModel {

    private final ConversionHistoryRepository repository;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final LiveData<List<ConversionHistory>> history;
    private final MutableLiveData<Set<Long>> selectedIds = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<Long> storageBytes = new MutableLiveData<>(0L);
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        repository = new ConversionHistoryRepository(application);
        history = Transformations.switchMap(searchQuery, query -> repository.observeHistory(query));
        refreshStorage();
    }

    public LiveData<List<ConversionHistory>> getHistory() { return history; }
    public LiveData<Set<Long>> getSelectedIds() { return selectedIds; }
    public LiveData<Long> getStorageBytes() { return storageBytes; }
    public LiveData<Event<String>> getMessages() { return messages; }

    public void setSearchQuery(String query) {
        String normalized = query == null ? "" : query;
        if (!normalized.equals(searchQuery.getValue())) {
            searchQuery.setValue(normalized);
        }
    }

    public boolean isSelectionMode() { return !currentSelection().isEmpty(); }

    public void toggleSelection(long id) {
        Set<Long> updated = new HashSet<>(currentSelection());
        if (!updated.add(id)) {
            updated.remove(id);
        }
        selectedIds.setValue(updated);
    }

    public void selectAll() {
        List<ConversionHistory> list = history.getValue();
        if (list == null) return;
        Set<Long> all = new HashSet<>();
        for (ConversionHistory entry : list) {
            all.add(entry.getId());
        }
        selectedIds.setValue(all);
    }

    public void clearSelection() { selectedIds.setValue(Collections.emptySet()); }

    @NonNull
    public List<ConversionHistory> getSelectedEntries() {
        List<ConversionHistory> result = new ArrayList<>();
        List<ConversionHistory> list = history.getValue();
        Set<Long> selection = currentSelection();
        if (list != null) {
            for (ConversionHistory entry : list) {
                if (selection.contains(entry.getId())) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    @NonNull
    public List<File> getSelectedFiles() {
        List<File> files = new ArrayList<>();
        for (ConversionHistory entry : getSelectedEntries()) {
            if (entry.getFilePath() != null) {
                File file = new File(entry.getFilePath());
                if (file.exists()) files.add(file);
            }
        }
        return files;
    }

    public void deleteSelected(boolean deleteFiles) {
        List<ConversionHistory> entries = getSelectedEntries();
        if (entries.isEmpty()) return;
        repository.deleteEntries(entries, deleteFiles, deleteCallback());
    }

    public void clearAll(boolean deleteFiles) {
        repository.clearAll(deleteFiles, deleteCallback());
    }

    public void syncWithDisk() {
        repository.syncWithDisk(new ConversionHistoryRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer removed) {
                if (removed != null && removed > 0) {
                    postMessage("Sincronizado: " + removed + " registos órfãos removidos.");
                }
                refreshStorage();
            }
            @Override
            public void onError(@NonNull Exception error) {}
        });
    }

    public void refreshStorage() {
        repository.calculateStorageSize(new ConversionHistoryRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long bytes) { storageBytes.setValue(bytes == null ? 0L : bytes); }
            @Override
            public void onError(@NonNull Exception error) {}
        });
    }

    private ConversionHistoryRepository.Callback<ConversionHistoryRepository.DeleteSummary> deleteCallback() {
        return new ConversionHistoryRepository.Callback<ConversionHistoryRepository.DeleteSummary>() {
            @Override
            public void onSuccess(ConversionHistoryRepository.DeleteSummary summary) {
                clearSelection();
                postMessage("Removidos " + summary.removedRecords + " itens (" + FileStorageManager.formatSize(summary.freedBytes) + " libertados).");
                refreshStorage();
            }
            @Override
            public void onError(@NonNull Exception error) {
                postMessage(error.getMessage() != null ? error.getMessage() : error.toString());
            }
        };
    }

    @NonNull
    private Set<Long> currentSelection() {
        Set<Long> value = selectedIds.getValue();
        return value != null ? value : Collections.emptySet();
    }

    private void postMessage(@NonNull String message) {
        messages.setValue(new Event<>(message));
    }
}