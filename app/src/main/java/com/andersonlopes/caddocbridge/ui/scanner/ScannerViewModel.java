package com.andersonlopes.caddocbridge.ui.scanner;

import android.app.Application;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.andersonlopes.caddocbridge.repository.ConversionHistoryRepository;
import com.andersonlopes.caddocbridge.utils.Event;
import com.andersonlopes.caddocbridge.vision.OcrProcessor;
import com.andersonlopes.caddocbridge.vision.OpenCvUtils;

import java.io.File;

public class ScannerViewModel extends AndroidViewModel {

    private final ConversionHistoryRepository repository;
    private final MutableLiveData<String> recognizedText = new MutableLiveData<>();
    private final MutableLiveData<String> extractedTextResult = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> processedImage = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> setIsLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        this.repository = new ConversionHistoryRepository(application);
    }

    public LiveData<String> getRecognizedText() {
        return recognizedText;
    }

    public LiveData<String> getExtractedTextResult() {
        return extractedTextResult;
    }

    public LiveData<Bitmap> getProcessedImage() {
        return processedImage;
    }

    public LiveData<Event<String>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> getIsLoading() {
        return setIsLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void applyDocumentFilter(Bitmap bitmap) {
        if (bitmap == null) return;
        Bitmap filtered = OpenCvUtils.processDocumentFilter(bitmap);
        processedImage.setValue(filtered != null ? filtered : bitmap);
    }

    public void runOcr(Bitmap bitmap) {
        if (bitmap == null) {
            messages.setValue(new Event<>("Imagem inválida para OCR."));
            return;
        }
        setIsLoading.setValue(true);
        OcrProcessor.extractText(bitmap, new OcrProcessor.OcrCallback() {
            @Override
            public void onSuccess(String text) {
                setIsLoading.setValue(false);
                extractedTextResult.setValue(text);
                recognizedText.setValue(text);
            }

            @Override
            public void onFailure(@NonNull Exception e) {
                setIsLoading.setValue(false);
                messages.setValue(new Event<>("Erro no OCR: " + e.getMessage()));
            }
        });
    }

    public void processImage(Bitmap bitmap) {
        applyDocumentFilter(bitmap);
        if (processedImage.getValue() != null) {
            runOcr(processedImage.getValue());
        }
    }

    public void saveScannedDocument(File file) {
        if (file == null) return;
        repository.recordConversion(file, new ConversionHistoryRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long result) {
                messages.setValue(new Event<>("Documento guardado com sucesso!"));
            }

            @Override
            public void onError(@NonNull Exception error) {
                messages.setValue(new Event<>("Erro ao guardar: " + error.getMessage()));
            }
        });
    }
}