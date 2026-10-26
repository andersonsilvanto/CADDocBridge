package com.andersonlopes.caddocbridge.ui.history;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.andersonlopes.caddocbridge.R;
import com.andersonlopes.caddocbridge.model.ConversionHistory;
import com.andersonlopes.caddocbridge.utils.Event;
import com.andersonlopes.caddocbridge.utils.FileShareHelper;
import com.andersonlopes.caddocbridge.utils.FileStorageManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

public class HistoryActivity extends AppCompatActivity implements HistoryAdapter.Listener {

    private HistoryViewModel viewModel;
    private HistoryAdapter adapter;

    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView storageView;
    private View selectionBar;

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            viewModel.clearSelection();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);

        bindViews();
        setupList();
        setupSearch();
        setupSelectionBar();
        observeViewModel();

        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Remove do histórico os registros cujos arquivos foram apagados fora do app.
        viewModel.syncWithDisk();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.recyclerHistory);
        emptyView = findViewById(R.id.textEmpty);
        storageView = findViewById(R.id.textStorage);
        selectionBar = findViewById(R.id.selectionBar);
    }

    private void setupList() {
        adapter = new HistoryAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        EditText search = findViewById(R.id.editSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSelectionBar() {
        findViewById(R.id.buttonCancel).setOnClickListener(v -> viewModel.clearSelection());
        findViewById(R.id.buttonSelectAll).setOnClickListener(v -> viewModel.selectAll());
        findViewById(R.id.buttonShare).setOnClickListener(v -> shareSelected());
        findViewById(R.id.buttonDelete).setOnClickListener(v -> showDeleteDialog());
    }

    private void observeViewModel() {
        viewModel.getHistory().observe(this, list -> {
            adapter.submitList(list);
            boolean empty = list == null || list.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getSelectedIds().observe(this, ids -> {
            adapter.setSelectedIds(ids);
            boolean selecting = ids != null && !ids.isEmpty();
            selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
            backCallback.setEnabled(selecting);
        });

        viewModel.getStorageBytes().observe(this, bytes ->
                storageView.setText("Espaço ocupado: "
                        + FileStorageManager.formatSize(bytes == null ? 0L : bytes)));

        viewModel.getMessages().observe(this, (Event<String> event) -> {
            if (event == null) return;
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------ itens da lista

    @Override
    public void onItemClick(@NonNull ConversionHistory item) {
        if (viewModel.isSelectionMode()) {
            viewModel.toggleSelection(item.getId());
            return;
        }
        openEntry(item);
    }

    @Override
    public void onItemLongClick(@NonNull ConversionHistory item) {
        viewModel.toggleSelection(item.getId());
    }

    private void openEntry(@NonNull ConversionHistory item) {
        String path = item.getFilePath();
        File file = path == null ? null : new File(path);
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Arquivo não encontrado. O histórico será atualizado.",
                    Toast.LENGTH_SHORT).show();
            viewModel.syncWithDisk();
            return;
        }
        if (file.isDirectory()) {
            Toast.makeText(this, "Este item é uma pasta de arquivos (lote).",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!FileShareHelper.open(this, file)) {
            Toast.makeText(this, "Nenhum app encontrado para abrir este arquivo.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------- ações

    private void shareSelected() {
        List<File> files = viewModel.getSelectedFiles();
        if (!FileShareHelper.share(this, files, "Compartilhar via")) {
            Toast.makeText(this, "Nenhum arquivo disponível para compartilhar.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Exclusão sempre com confirmação: apagar arquivos do aparelho não tem volta. */
    private void showDeleteDialog() {
        int count = viewModel.getSelectedEntries().size();
        if (count == 0) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Excluir " + count + " item(ns)?")
                .setMessage("Escolha se os arquivos também devem ser apagados do aparelho.")
                .setPositiveButton("Registro e arquivos", (d, w) -> viewModel.deleteSelected(true))
                .setNeutralButton("Só do histórico", (d, w) -> viewModel.deleteSelected(false))
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
