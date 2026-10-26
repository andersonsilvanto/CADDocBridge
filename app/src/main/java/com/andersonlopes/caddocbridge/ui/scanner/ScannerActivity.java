package com.andersonlopes.caddocbridge.ui.scanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.andersonlopes.caddocbridge.R;
import com.andersonlopes.caddocbridge.utils.Event;

public class ScannerActivity extends AppCompatActivity {

    private ScannerViewModel viewModel;
    private ImageView imageViewPreview;
    private TextView textOcrResult;
    private Bitmap sampleBitmap; // Bitmap de exemplo ou carregado da câmara/galeria

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        viewModel = new ViewModelProvider(this).get(ScannerViewModel.class);

        bindViews();
        observeViewModel();
        setupListeners();
    }

    private void bindViews() {
        imageViewPreview = findViewById(R.id.imageViewPreview);
        textOcrResult = findViewById(R.id.textOcrResult);
    }

    private void setupListeners() {
        Button btnFilter = findViewById(R.id.buttonOpenCvFilter);
        Button btnOcr = findViewById(R.id.buttonRunOcr);

        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> {
                if (sampleBitmap != null) {
                    viewModel.applyDocumentFilter(sampleBitmap);
                } else {
                    Toast.makeText(this, "Carregue ou selecione uma imagem primeiro.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnOcr != null) {
            btnOcr.setOnClickListener(v -> {
                if (sampleBitmap != null) {
                    viewModel.runOcr(sampleBitmap);
                } else {
                    Toast.makeText(this, "Carregue ou selecione uma imagem primeiro.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void observeViewModel() {
        viewModel.getProcessedImage().observe(this, bitmap -> {
            if (bitmap != null && imageViewPreview != null) {
                imageViewPreview.setImageBitmap(bitmap);
            }
        });

        viewModel.getExtractedTextResult().observe(this, text -> {
            if (textOcrResult != null) {
                textOcrResult.setText(text);
            }
        });

        viewModel.getMessages().observe(this, (Event<String> event) -> {
            if (event == null) return;
            String msg = event.getContentIfNotHandled();
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
