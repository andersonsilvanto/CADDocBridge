package com.andersonlopes.caddocbridge;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.content.IntentCompat;

import com.andersonlopes.caddocbridge.repository.ConversionHistoryRepository;
import com.andersonlopes.caddocbridge.ui.history.HistoryActivity;
import com.andersonlopes.caddocbridge.utils.DocxWriter;
import com.andersonlopes.caddocbridge.utils.FileStorageManager;
import com.andersonlopes.caddocbridge.vision.OcrProcessor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String OUTPUT_FOLDER = "CadDocBridge_Files";
    private static final String DOWNLOADS_SUBFOLDER = "CadDocBridge";

    private static final int SCAN_DOC = 0;
    private static final int SCAN_PLANT = 1;
    private static final int SCAN_TABLE = 2;
    private static final int SCAN_OCR = 3;

    private MaterialCardView cardSelectFile;
    private LinearLayout layoutFileInfo;
    private TextView txtFileName, txtDetectedFormat, txtHistoryLog;
    private Spinner spinnerTargetFormat;
    private ProgressBar progressBar;
    private MaterialButton btnConvert, btnScanPlant, btnScanTable, btnScanDoc, btnScanOcr, btnHistory, btnOpenFolder, btnShareFile;

    private Uri selectedFileUri;
    private String selectedFileName = "";
    private String selectedExtension = "";
    private int pendingScanMode = SCAN_DOC;
    private final StringBuilder historyBuilder = new StringBuilder();

    private final Map<String, List<String>> conversionRules = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Variável para a pasta de ficheiros da app
    private File appFolder;
    private ConversionHistoryRepository historyRepository;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        processSelectedFile(uri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<IntentSenderRequest> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK) return;
                GmsDocumentScanningResult scan =
                        GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                if (scan == null || scan.getPages() == null || scan.getPages().isEmpty()) {
                    Toast.makeText(this, "O scanner não retornou nenhuma página.", Toast.LENGTH_SHORT).show();
                    return;
                }
                applyScanResult(scan.getPages().get(0).getImageUri());
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicialização correta da pasta principal no ciclo de vida
        // Pasta única de saída: getExternalFilesDir(null)/CadDocBridge_Files
        appFolder = new FileStorageManager(this).getBaseDir();
        historyRepository = new ConversionHistoryRepository(this);

        initViews();
        setupConversionRules();
        setupListeners();

        btnShareFile.setEnabled(false); // habilita depois da primeira conversão

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void initViews() {
        cardSelectFile = findViewById(R.id.cardSelectFile);
        layoutFileInfo = findViewById(R.id.layoutFileInfo);
        txtFileName = findViewById(R.id.txtFileName);
        txtDetectedFormat = findViewById(R.id.txtDetectedFormat);
        txtHistoryLog = findViewById(R.id.txtHistoryLog);
        spinnerTargetFormat = findViewById(R.id.spinnerTargetFormat);
        progressBar = findViewById(R.id.progressBar);
        btnConvert = findViewById(R.id.btnConvert);
        btnScanPlant = findViewById(R.id.btnScanPlant);
        btnScanTable = findViewById(R.id.btnScanTable);
        btnScanDoc = findViewById(R.id.btnScanDoc);
        btnScanOcr = findViewById(R.id.btnScanOcr);
        btnOpenFolder = findViewById(R.id.btnOpenFolder);
        btnHistory = findViewById(R.id.btnHistory);
        btnShareFile = findViewById(R.id.btnShareFile);
    }
    // Somente conversões realmente implementadas
    private void setupConversionRules() {
        conversionRules.clear(); // Limpa regras anteriores para evitar duplicados

        conversionRules.put("dxf", Arrays.asList("PDF (Documento Técnico)", "PNG", "JPG"));
        conversionRules.put("jpg", Arrays.asList("PDF (Documento A4)", "PNG"));
        conversionRules.put("jpeg", Arrays.asList("PDF (Documento A4)", "PNG"));
        conversionRules.put("png", Arrays.asList("PDF (Documento A4)", "JPG"));
        conversionRules.put("docx", Arrays.asList("PDF (Documento A4)"));
    }
    private void setupListeners() {
        cardSelectFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(Intent.createChooser(intent, "Selecione o ficheiro"));
        });

        btnScanPlant.setOnClickListener(v -> startScanner(SCAN_PLANT));
        btnScanTable.setOnClickListener(v -> startScanner(SCAN_TABLE));
        btnScanDoc.setOnClickListener(v -> startScanner(SCAN_DOC));
        btnScanOcr.setOnClickListener(v -> startScanner(SCAN_OCR));

        btnOpenFolder.setOnClickListener(v -> openConvertedFolder());
        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        btnShareFile.setOnClickListener(v -> shareCurrentFile());
        btnConvert.setOnClickListener(v -> executeConversionWithProgress());
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            Uri incomingUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
            if (incomingUri != null) {
                processSelectedFile(incomingUri);
                Toast.makeText(this, "Ficheiro recebido com sucesso!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------- Scanner

    private void startScanner(int mode) {
        pendingScanMode = mode;

        // ML Kit Document Scanner: detecção de bordas, correção de perspectiva e limpeza
        // (o resultado volta em scannerLauncher -> applyScanResult -> spinner de formatos).
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();

        GmsDocumentScanning.getClient(options)
                .getStartScanIntent(this)
                .addOnSuccessListener(intentSender ->
                        scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build()))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Não foi possível abrir o scanner: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void applyScanResult(Uri imageUri) {
        String label;
        List<String> targets;
        switch (pendingScanMode) {
            case SCAN_PLANT:
                label = "Planta";
                targets = Arrays.asList("PDF (Técnico)", "PNG");
                break;
            case SCAN_TABLE:
                label = "Tabela";
                targets = Arrays.asList("PDF (Tabela)", "PNG");
                break;
            case SCAN_OCR:
                label = "OCR";
                targets = Arrays.asList("TXT (Texto reconhecido)", "DOCX (Documento Word)");
                break;
            default:
                label = "Documento";
                targets = Arrays.asList("PDF (Documento A4)", "PNG");
                break;
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        selectedFileUri = imageUri;
        selectedFileName = label + "_" + stamp + ".jpg";
        selectedExtension = "jpg";

        txtFileName.setText("Ficheiro: " + selectedFileName);
        txtDetectedFormat.setText("Origem: Scanner (câmera)");
        layoutFileInfo.setVisibility(View.VISIBLE);
        populateSpinner(targets);
    }

    // ------------------------------------------------------- Seleção de ficheiro

    private void processSelectedFile(Uri uri) {
        String name = getFileNameFromUri(uri);
        String ext = getExtension(name).toLowerCase(Locale.ROOT);
        List<String> availableTargets = conversionRules.get(ext);

        if (availableTargets == null) {
            selectedFileUri = null;
            selectedFileName = "";
            selectedExtension = "";
            layoutFileInfo.setVisibility(View.GONE);
            String hint = ext.equals("dwg")
                    ? "Arquivos DWG não são suportados. No AutoCAD, use Salvar como > DXF e converta o DXF aqui."
                    : "Formato ." + ext.toUpperCase(Locale.ROOT) + " ainda não suportado. Use DXF, JPG, PNG ou DOCX.";
            Toast.makeText(this, hint, Toast.LENGTH_LONG).show();
            return;
        }

        selectedFileUri = uri;
        selectedFileName = name;
        selectedExtension = ext;

        txtFileName.setText("Ficheiro: " + selectedFileName);
        txtDetectedFormat.setText("Formato Detetado: ." + selectedExtension.toUpperCase(Locale.ROOT));
        layoutFileInfo.setVisibility(View.VISIBLE);
        populateSpinner(availableTargets);
    }

    private void populateSpinner(List<String> options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetFormat.setAdapter(adapter);
    }

    // ----------------------------------------------------------------- Conversão

    private void executeConversionWithProgress() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Selecione um ficheiro ou use o scanner primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (spinnerTargetFormat.getSelectedItem() == null) {
            Toast.makeText(this, "Selecione um formato de destino.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String targetFormat = spinnerTargetFormat.getSelectedItem().toString();

        String baseName = "CAD_Doc_" + System.currentTimeMillis();
        if (!selectedFileName.isEmpty()) {
            int dotIndex = selectedFileName.lastIndexOf('.');
            baseName = (dotIndex != -1) ? selectedFileName.substring(0, dotIndex) : selectedFileName;
            baseName += "_convertido";
        }
        final String finalDefaultName = baseName;

        final EditText input = new EditText(this);
        input.setText(finalDefaultName);
        input.setSelection(input.getText().length());
        input.setPadding(50, 30, 50, 30);

        new AlertDialog.Builder(this)
                .setTitle("✏️ Nome do Ficheiro de Saída")
                .setMessage("Personalize o nome para evitar duplicidade:")
                .setView(input)
                .setPositiveButton("Converter e Salvar", (dialog, which) -> {
                    String textInput = input.getText().toString().trim();
                    String customName = textInput.isEmpty() ? finalDefaultName : textInput;
                    startActualConversion(targetFormat, customName);
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void startActualConversion(String targetFormat, String customName) {
        progressBar.setVisibility(View.VISIBLE);
        btnConvert.setEnabled(false);

        final Uri sourceUri = selectedFileUri;
        final String sourceExt = selectedExtension;

        executor.execute(() -> {
            File output = null;
            String error = null;
            boolean exported = false;

            try {
                output = saveOutputDocument(sourceUri, sourceExt, targetFormat, customName);
                exported = exportToDownloads(output);
            } catch (UnsupportedOperationException e) {
                error = e.getMessage();
            } catch (OutOfMemoryError e) {
                error = "Memória insuficiente para processar este ficheiro.";
            } catch (Exception e) {
                e.printStackTrace();
                error = "Erro ao gerar o ficheiro: " + e.getMessage();
            }

            final File finalOutput = output;
            final String finalError = error;
            final boolean finalExported = exported;
            final String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                progressBar.setVisibility(View.GONE);
                btnConvert.setEnabled(true);

                String logEntry;
                if (finalOutput != null) {
                    btnShareFile.setEnabled(true);
                    historyRepository.recordConversion(finalOutput, null); // Histórico (Room)
                    logEntry = String.format("• [%s] %s [Sucesso]\n", timeStamp, finalOutput.getName());
                    Toast.makeText(this,
                            finalExported
                                    ? "Salvo em Downloads/" + DOWNLOADS_SUBFOLDER
                                    : "Ficheiro guardado na pasta do aplicativo.",
                            Toast.LENGTH_LONG).show();
                } else {
                    logEntry = String.format("• [%s] Falha: %s\n", timeStamp, finalError);
                    Toast.makeText(this, finalError, Toast.LENGTH_LONG).show();
                }

                historyBuilder.insert(0, logEntry);
                txtHistoryLog.setText(historyBuilder.toString());
            });
        });
    }

    /**
     * Conversões reais: DXF -> PDF/PNG e imagem (JPG/PNG) -> PDF/PNG/JPG.
     * Roda fora da thread principal.
     */
    private File saveOutputDocument(Uri sourceUri, String sourceExt, String targetFormat,
                                    String customName) throws IOException {

        // Normalizamos a extensão para minúsculas para evitar problemas com .JPG vs .jpg
        if (sourceExt != null) {
            sourceExt = sourceExt.toLowerCase(Locale.ROOT);
        } else {
            sourceExt = "";
        }

        String targetExt;
        if (targetFormat.contains("PDF")) targetExt = ".pdf";
        else if (targetFormat.contains("PNG")) targetExt = ".png";
        else if (targetFormat.contains("JPG")) targetExt = ".jpg";
        else if (targetFormat.startsWith("TXT")) targetExt = ".txt";
        else if (targetFormat.startsWith("DOCX")) targetExt = ".docx";
        else throw new UnsupportedOperationException("Formato de destino ainda não suportado.");

        boolean isDxf = sourceExt.equals("dxf");
        boolean isImage = sourceExt.equals("jpg") || sourceExt.equals("jpeg") || sourceExt.equals("png");
        boolean isDocx = sourceExt.equals("docx");
        boolean isOcrTarget = targetExt.equals(".txt") || targetExt.equals(".docx");

        if (!isDxf && !isImage && !isDocx) {
            throw new UnsupportedOperationException(
                    "Conversão de ." + sourceExt.toUpperCase(Locale.ROOT) + " ainda não suportada.");
        }

        // TXT e DOCX só existem como saída do OCR de imagens.
        if (isOcrTarget && !isImage) {
            throw new UnsupportedOperationException("TXT e DOCX só estão disponíveis para imagens (OCR).");
        }

        // ... (restante código de salvamento)

        String safeName = sanitizeFileName(customName);
        if (safeName.toLowerCase(Locale.ROOT).endsWith(targetExt)) {
            safeName = safeName.substring(0, safeName.length() - targetExt.length());
        }
        File outFile = uniqueFile(appFolder, safeName, targetExt);

        try (OutputStream out = new FileOutputStream(outFile)) {
            if (isOcrTarget) {
                convertImageToText(sourceUri, targetExt, out);
            } else if (isDxf) {
                convertDxf(sourceUri, targetExt, out);
            } else if (isImage) {
                convertImage(sourceUri, targetExt, out);
            } else if (isDocx) {
                // Conversão direta de DOCX para PDF usando Apache POI via Stream
                try (InputStream docxInput = getContentResolver().openInputStream(sourceUri)) {
                    if (docxInput == null) {
                        throw new IOException("Não foi possível abrir o fluxo do ficheiro DOCX.");
                    }

                    org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(docxInput);

                    PdfDocument pdfDocument = new PdfDocument();
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
                    PdfDocument.Page page = pdfDocument.startPage(pageInfo);

                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);
                    Paint paint = new Paint();
                    paint.setColor(Color.BLACK);
                    paint.setTextSize(12f);

                    int x = 40;
                    int y = 50;
                    int lineHeight = 20;

                    for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
                        String text = paragraph.getText();
                        if (text != null && !text.isEmpty()) {
                            canvas.drawText(text, x, y, paint);
                            y += lineHeight;

                            if (y > 800) {
                                pdfDocument.finishPage(page);
                                pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pdfDocument.getPages().size() + 1).create();
                                page = pdfDocument.startPage(pageInfo);
                                canvas = page.getCanvas();
                                canvas.drawColor(Color.WHITE);
                                y = 50;
                            }
                        }
                    }

                    pdfDocument.finishPage(page);
                    pdfDocument.writeTo(out);

                    document.close();
                    pdfDocument.close();
                }
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            if (e instanceof UnsupportedOperationException) throw (UnsupportedOperationException) e;
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Erro ao converter documento: " + e.getMessage(), e);
        }

        return outFile;
    }

    /** OCR offline (ML Kit): imagem -> texto reconhecido, salvo como TXT ou DOCX. */
    private void convertImageToText(Uri sourceUri, String targetExt, OutputStream out) throws IOException {
        Bitmap bitmap = decodeBitmap(sourceUri, 3000);
        String text;
        try {
            text = OcrProcessor.recognizeTextBlocking(bitmap);
        } finally {
            bitmap.recycle();
        }
        if (text == null || text.trim().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Nenhum texto reconhecido na imagem. Tente uma foto mais nítida e bem iluminada.");
        }
        if (targetExt.equals(".docx")) {
            DocxWriter.write(text, out);
        } else {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void convertImage(Uri sourceUri, String targetExt, OutputStream out) throws IOException {
        Bitmap bitmap = decodeBitmap(sourceUri, 2500);
        try {
            switch (targetExt) {
                case ".pdf":
                    writeBitmapAsPdf(bitmap, out);
                    break;
                case ".png":
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    break;
                default:
                    Bitmap flat = flattenOnWhite(bitmap);
                    flat.compress(Bitmap.CompressFormat.JPEG, 95, out);
                    if (flat != bitmap) flat.recycle();
                    break;
            }
        } finally {
            bitmap.recycle();
        }
    }

    private void convertDxf(Uri sourceUri, String targetExt, OutputStream out) throws IOException {
        DxfRenderer dxf;
        try (InputStream in = getContentResolver().openInputStream(sourceUri)) {
            if (in == null) throw new IOException("Não foi possível abrir o ficheiro de origem");
            dxf = DxfRenderer.parse(in);
        }
        if (dxf.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Nenhum elemento desenhável encontrado no DXF (hachuras e sólidos não são suportados).");
        }

        boolean landscape = dxf.getWidth() >= dxf.getHeight();

        if (targetExt.equals(".pdf")) {
            int pageW = landscape ? 842 : 595;
            int pageH = landscape ? 595 : 842;
            PdfDocument pdf = new PdfDocument();
            try {
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, 1).create());
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                dxf.draw(canvas, pageW, pageH, 28f, 0.6f);
                pdf.finishPage(page);
                pdf.writeTo(out);
            } finally {
                pdf.close();
            }
        } else {
            final int maxSide = 3000;
            double ratio = dxf.getHeight() / dxf.getWidth();
            int pw, ph;
            if (dxf.getWidth() >= dxf.getHeight()) {
                pw = maxSide;
                ph = (int) Math.max(200, Math.round(maxSide * ratio));
            } else {
                ph = maxSide;
                pw = (int) Math.max(200, Math.round(maxSide / ratio));
            }
            Bitmap bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bmp);
                canvas.drawColor(Color.WHITE);
                dxf.draw(canvas, pw, ph, 40f, 2f);
                if (targetExt.equals(".jpg")) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, out);
                } else {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } finally {
                bmp.recycle();
            }
        }
    }

    private Bitmap decodeBitmap(Uri uri, int maxSide) throws IOException {
        ContentResolver resolver = getContentResolver();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Não foi possível abrir o ficheiro de origem");
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Não foi possível abrir o ficheiro de origem");
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) throw new IOException("Imagem inválida ou corrompida");
            return bmp;
        }
    }

    private void writeBitmapAsPdf(Bitmap bitmap, OutputStream out) throws IOException {
        final int pageW = 595;
        final int pageH = 842;

        PdfDocument pdf = new PdfDocument();
        try {
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, 1).create());
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);

            float scale = Math.min((float) pageW / bitmap.getWidth(), (float) pageH / bitmap.getHeight());
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            float left = (pageW - w) / 2f;
            float top = (pageH - h) / 2f;
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + w, top + h), null);

            pdf.finishPage(page);
            pdf.writeTo(out);
        } finally {
            pdf.close();
        }
    }

    private Bitmap flattenOnWhite(Bitmap src) {
        if (!src.hasAlpha()) return src;
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(src, 0, 0, null);
        return result;
    }

    // ------------------------------------------------- Nomes e pastas de saída

    private String sanitizeFileName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        clean = clean.replaceAll("^\\.+", "");
        if (clean.isEmpty()) clean = "CAD_Doc_" + System.currentTimeMillis();
        return clean;
    }

    private File uniqueFile(File folder, String baseName, String ext) {
        File file = new File(folder, baseName + ext);
        int counter = 1;
        while (file.exists()) {
            file = new File(folder, baseName + " (" + counter + ")" + ext);
            counter++;
        }
        return file;
    }

    private File getOutputFolder() {
        return new File(getExternalFilesDir(null), OUTPUT_FOLDER);
    }

    /** Copia o ficheiro para Downloads/CadDocBridge (Android 10+), visível no gerenciador de arquivos. */
    private boolean exportToDownloads(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;

        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_SUBFOLDER);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;

        try (InputStream in = new FileInputStream(file);
             OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("Sem acesso ao destino");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
            resolver.delete(uri, null, null);
            return false;
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return true;
    }

    private void openConvertedFolder() {
        File appFolder = getOutputFolder();
        if (!appFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            appFolder.mkdirs();
        }

        final File[] files = appFolder.listFiles();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("📁 Ficheiros convertidos")
                .setPositiveButton("Fechar", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Histórico", (dialog, which) ->
                        startActivity(new Intent(MainActivity.this, HistoryActivity.class)))
                .setNeutralButton("Abrir Downloads", (dialog, which) -> {
                    try {
                        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, "Abra o app Arquivos > Downloads > " + DOWNLOADS_SUBFOLDER,
                                Toast.LENGTH_LONG).show();
                    }
                });

        if (files == null || files.length == 0) {
            builder.setMessage("Nenhum ficheiro convertido ainda. Realize uma conversão primeiro.");
        } else {
            String[] fileNames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                fileNames[i] = "📄 " + files[i].getName() + " (" + (files[i].length() / 1024) + " KB)";
            }
            builder.setItems(fileNames, (dialog, which) -> openFileWithIntent(files[which]));
        }
        builder.show();
    }

    private String getMimeType(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "*/*";
    }

    private void openFileWithIntent(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getMimeType(file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Toast.makeText(this, "FileProvider não configurado corretamente no Manifest.", Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nenhum aplicativo encontrado para abrir este formato.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCurrentFile() {
        File[] files = getOutputFolder().listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(this, "Nenhum ficheiro convertido para partilhar.", Toast.LENGTH_SHORT).show();
            return;
        }

        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) latest = f;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", latest);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(getMimeType(latest));
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Partilhar Ficheiro Convertido via..."));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Toast.makeText(this, "FileProvider não configurado corretamente no Manifest.", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ Helpers

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null && uri.getPath() != null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return (result != null && !result.isEmpty()) ? result : "ficheiro_desconhecido";
    }

    private String getExtension(String fileName) {
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex == -1) return "";
        return fileName.substring(lastIndex + 1);
    }

    // ======================================================================
    // Leitor/desenhista de DXF (classe interna, para manter tudo em um só arquivo)
    // ======================================================================
    /**
     * Leitor e desenhista simples de arquivos DXF (ASCII).
     * Suporta: LINE, LWPOLYLINE (com arcos), POLYLINE, CIRCLE, ARC, ELLIPSE,
     * SPLINE (aproximada), TEXT, MTEXT, INSERT (blocos) e DIMENSION (via bloco).
     * Não suporta: HATCH, SOLID, imagens, DXF binário.
     */
    private static final class DxfRenderer {

        private static final int MAX_INSERT_DEPTH = 8;
        private static final long MAX_BYTES = 80L * 1024 * 1024;
        private static final double ARC_STEP = Math.toRadians(3);

        // ---------- Estruturas internas ----------

        private static final class Raw {
            final String type;
            final List<Integer> codes = new ArrayList<>();
            final List<String> values = new ArrayList<>();

            Raw(String type) {
                this.type = type;
            }

            void add(int code, String value) {
                codes.add(code);
                values.add(value);
            }

            String str(int code, String def) {
                for (int i = 0; i < codes.size(); i++) {
                    if (codes.get(i) == code) return values.get(i).trim();
                }
                return def;
            }

            double num(int code, double def) {
                String s = str(code, null);
                if (s == null) return def;
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return def;
                }
            }

            int integer(int code, int def) {
                String s = str(code, null);
                if (s == null) return def;
                try {
                    return (int) Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return def;
                }
            }
        }

        private static final class Block {
            final String name;
            final double baseX, baseY;
            final List<Raw> entities = new ArrayList<>();

            Block(String name, double baseX, double baseY) {
                this.name = name;
                this.baseX = baseX;
                this.baseY = baseY;
            }
        }

        private static final class Poly {
            final double[] x, y;
            final boolean closed;
            final int color;

            Poly(double[] x, double[] y, boolean closed, int color) {
                this.x = x;
                this.y = y;
                this.closed = closed;
                this.color = color;
            }
        }

        private static final class Txt {
            final double x, y, height, rotation;
            final String text;
            final int color;
            final boolean topAligned;

            Txt(double x, double y, double height, double rotation, String text, int color, boolean topAligned) {
                this.x = x;
                this.y = y;
                this.height = height;
                this.rotation = rotation;
                this.text = text;
                this.color = color;
                this.topAligned = topAligned;
            }
        }

        // ---------- Estado ----------

        private final Map<String, Integer> layerColors = new HashMap<>();
        private final Map<String, Block> blocks = new HashMap<>();
        private final List<Raw> modelEntities = new ArrayList<>();
        private final List<Poly> polys = new ArrayList<>();
        private final List<Txt> texts = new ArrayList<>();

        private String section = null;
        private Block currentBlock = null;

        private double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        private double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        private DxfRenderer() {
        }

        // ---------- API pública ----------

        public static DxfRenderer parse(InputStream in) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new IOException("Arquivo DXF muito grande (limite de 80 MB).");
                bos.write(buf, 0, n);
            }
            byte[] data = bos.toByteArray();

            if (data.length >= 18 && new String(data, 0, 18, StandardCharsets.ISO_8859_1).startsWith("AutoCAD Binary DXF")) {
                throw new IOException("DXF binário não é suportado. Salve o DXF em formato ASCII.");
            }

            String text = new String(data, StandardCharsets.UTF_8);
            if (text.indexOf('\uFFFD') >= 0) {
                text = new String(data, Charset.forName("windows-1252"));
            }

            DxfRenderer dxf = new DxfRenderer();
            dxf.readPairs(text);
            dxf.buildEntities(dxf.modelEntities, new double[]{1, 0, 0, 1, 0, 0}, 7, 0);
            dxf.finishBounds();
            return dxf;
        }

        public boolean isEmpty() {
            return polys.isEmpty() && texts.isEmpty();
        }

        public double getWidth() {
            return maxX - minX;
        }

        public double getHeight() {
            return maxY - minY;
        }

        /** Desenha o conteúdo ajustado à área (pageW x pageH), com margem, y invertido. */
        public void draw(Canvas canvas, float pageW, float pageH, float margin, float strokeWidth) {
            double dx = getWidth();
            double dy = getHeight();
            double availW = pageW - 2 * margin;
            double availH = pageH - 2 * margin;
            double s = Math.min(availW / dx, availH / dy);
            double offX = margin + (availW - dx * s) / 2.0;
            double offY = margin + (availH - dy * s) / 2.0;

            Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(strokeWidth);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);

            for (Poly p : polys) {
                int n = p.x.length;
                if (n < 2) continue;
                Path path = new Path();
                for (int i = 0; i < n; i++) {
                    float px = (float) (offX + (p.x[i] - minX) * s);
                    float py = (float) (pageH - (offY + (p.y[i] - minY) * s));
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                if (p.closed) path.close();
                linePaint.setColor(aciToRgb(p.color));
                canvas.drawPath(path, linePaint);
            }

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setStyle(Paint.Style.FILL);

            for (Txt t : texts) {
                float size = (float) Math.max(t.height * s, 1.0);
                float px = (float) (offX + (t.x - minX) * s);
                float py = (float) (pageH - (offY + (t.y - minY) * s));

                textPaint.setColor(aciToRgb(t.color));
                textPaint.setTextSize(size);

                canvas.save();
                canvas.translate(px, py);
                canvas.rotate((float) -Math.toDegrees(t.rotation));
                float lineY = t.topAligned ? size * 0.9f : 0f;
                for (String line : t.text.split("\n")) {
                    canvas.drawText(line, 0, lineY, textPaint);
                    lineY += size * 1.2f;
                }
                canvas.restore();
            }
        }

        // ---------- Leitura do arquivo ----------

        private void readPairs(String text) throws IOException {
            BufferedReader br = new BufferedReader(new StringReader(text));
            Raw cur = null;
            boolean first = true;
            String codeLine;
            while ((codeLine = br.readLine()) != null) {
                String valueLine = br.readLine();
                if (valueLine == null) break;

                if (first) {
                    codeLine = codeLine.replace("\uFEFF", "");
                    first = false;
                }

                int code;
                try {
                    code = Integer.parseInt(codeLine.trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Arquivo DXF inválido ou não é ASCII.");
                }

                if (code == 0) {
                    if (cur != null) handleRaw(cur);
                    cur = new Raw(valueLine.trim());
                    if ("EOF".equals(cur.type)) {
                        cur = null;
                        break;
                    }
                } else if (cur != null) {
                    cur.add(code, valueLine);
                }
            }
            if (cur != null) handleRaw(cur);
        }

        private void handleRaw(Raw r) {
            if ("SECTION".equals(r.type)) {
                section = r.str(2, "");
                return;
            }
            if ("ENDSEC".equals(r.type)) {
                section = null;
                currentBlock = null;
                return;
            }

            if ("TABLES".equals(section)) {
                if ("LAYER".equals(r.type)) {
                    String name = r.str(2, "");
                    int color = r.integer(62, 7);
                    boolean frozen = (r.integer(70, 0) & 1) != 0;
                    layerColors.put(name, (color < 0 || frozen) ? -1 : (color == 0 ? 7 : color));
                }
            } else if ("BLOCKS".equals(section)) {
                if ("BLOCK".equals(r.type)) {
                    currentBlock = new Block(r.str(2, ""), r.num(10, 0), r.num(20, 0));
                    blocks.put(currentBlock.name, currentBlock);
                } else if ("ENDBLK".equals(r.type)) {
                    currentBlock = null;
                } else if (currentBlock != null) {
                    currentBlock.entities.add(r);
                }
            } else if ("ENTITIES".equals(section)) {
                modelEntities.add(r);
            }
        }

        // ---------- Geração de geometria ----------

        private int resolveColor(Raw r, int parentColor) {
            Integer layerColor = layerColors.get(r.str(8, "0"));
            if (layerColor != null && layerColor < 0) return -1; // camada desligada/congelada

            int c = r.integer(62, 256);
            if (c < 0) return -1;
            if (c == 0) return parentColor;
            if (c == 256) return layerColor != null ? layerColor : parentColor;
            return c;
        }

        private void buildEntities(List<Raw> list, double[] m, int parentColor, int depth) {
            for (int i = 0; i < list.size(); i++) {
                Raw r = list.get(i);
                int color = resolveColor(r, parentColor);

                if ("POLYLINE".equals(r.type)) {
                    int j = i + 1;
                    List<Raw> verts = new ArrayList<>();
                    while (j < list.size() && "VERTEX".equals(list.get(j).type)) {
                        verts.add(list.get(j));
                        j++;
                    }
                    if (j < list.size() && "SEQEND".equals(list.get(j).type)) j++;
                    i = j - 1;
                    if (color >= 0) addOldPolyline(r, verts, m, color);
                    continue;
                }

                if (color < 0) continue;

                switch (r.type) {
                    case "LINE": {
                        List<double[]> pts = new ArrayList<>();
                        pts.add(new double[]{r.num(10, 0), r.num(20, 0)});
                        pts.add(new double[]{r.num(11, 0), r.num(21, 0)});
                        addPoly(pts, false, m, color);
                        break;
                    }
                    case "CIRCLE":
                        addArc(r.num(10, 0), r.num(20, 0), r.num(40, 0), 0, 360, true, m, color);
                        break;
                    case "ARC":
                        addArc(r.num(10, 0), r.num(20, 0), r.num(40, 0), r.num(50, 0), r.num(51, 360), false, m, color);
                        break;
                    case "ELLIPSE":
                        addEllipse(r, m, color);
                        break;
                    case "SPLINE":
                        addSpline(r, m, color);
                        break;
                    case "LWPOLYLINE":
                        addLwPolyline(r, m, color);
                        break;
                    case "TEXT":
                        addText(r, m, color, false);
                        break;
                    case "MTEXT":
                        addText(r, m, color, true);
                        break;
                    case "INSERT":
                        addInsert(r, m, color, depth);
                        break;
                    case "DIMENSION": {
                        Block b = blocks.get(r.str(2, ""));
                        if (b != null && depth < MAX_INSERT_DEPTH) {
                            buildEntities(b.entities, m, color, depth + 1);
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        private void addInsert(Raw r, double[] m, int color, int depth) {
            if (depth >= MAX_INSERT_DEPTH) return;
            Block b = blocks.get(r.str(2, ""));
            if (b == null) return;

            double sx = r.num(41, 1);
            double sy = r.num(42, 1);
            double rot = Math.toRadians(r.num(50, 0));
            double ix = r.num(10, 0);
            double iy = r.num(20, 0);

            double[] toOrigin = {sx, 0, 0, sy, -sx * b.baseX, -sy * b.baseY};
            double[] rotate = {Math.cos(rot), Math.sin(rot), -Math.sin(rot), Math.cos(rot), 0, 0};
            double[] translate = {1, 0, 0, 1, ix, iy};

            double[] local = mul(translate, mul(rotate, toOrigin));
            buildEntities(b.entities, mul(m, local), color, depth + 1);
        }

        private void addLwPolyline(Raw r, double[] m, int color) {
            List<double[]> v = new ArrayList<>(); // {x, y, bulge}
            for (int i = 0; i < r.codes.size(); i++) {
                int code = r.codes.get(i);
                String val = r.values.get(i).trim();
                try {
                    if (code == 10) {
                        v.add(new double[]{Double.parseDouble(val), 0, 0});
                    } else if (code == 20 && !v.isEmpty()) {
                        v.get(v.size() - 1)[1] = Double.parseDouble(val);
                    } else if (code == 42 && !v.isEmpty()) {
                        v.get(v.size() - 1)[2] = Double.parseDouble(val);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            boolean closed = (r.integer(70, 0) & 1) != 0;
            addBulgePolyline(v, closed, m, color);
        }

        private void addOldPolyline(Raw header, List<Raw> verts, double[] m, int color) {
            int flags = header.integer(70, 0);
            if ((flags & (16 | 64)) != 0) return; // malhas 3D não suportadas

            List<double[]> v = new ArrayList<>();
            for (Raw vr : verts) {
                if ((vr.integer(70, 0) & 16) != 0) continue; // ponto de controle de spline
                v.add(new double[]{vr.num(10, 0), vr.num(20, 0), vr.num(42, 0)});
            }
            addBulgePolyline(v, (flags & 1) != 0, m, color);
        }

        private void addBulgePolyline(List<double[]> v, boolean closed, double[] m, int color) {
            int n = v.size();
            if (n < 2) return;
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                double[] p = v.get(i);
                pts.add(new double[]{p[0], p[1]});
                boolean hasNext = i < n - 1 || closed;
                if (hasNext && Math.abs(p[2]) > 1e-9) {
                    double[] q = v.get((i + 1) % n);
                    arcPoints(p[0], p[1], q[0], q[1], p[2], pts);
                }
            }
            addPoly(pts, closed, m, color);
        }

        private void arcPoints(double x1, double y1, double x2, double y2, double b, List<double[]> out) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double d = Math.hypot(dx, dy);
            if (d < 1e-12) return;

            double off = (d / 2.0) * (1 - b * b) / (2 * b);
            double cx = (x1 + x2) / 2.0 + off * (-dy / d);
            double cy = (y1 + y2) / 2.0 + off * (dx / d);
            double radius = Math.hypot(x1 - cx, y1 - cy);
            double a1 = Math.atan2(y1 - cy, x1 - cx);
            double sweep = 4 * Math.atan(b);

            int steps = (int) Math.max(2, Math.ceil(Math.abs(sweep) / ARC_STEP));
            for (int k = 1; k < steps; k++) {
                double a = a1 + sweep * k / steps;
                out.add(new double[]{cx + radius * Math.cos(a), cy + radius * Math.sin(a)});
            }
        }

        private void addArc(double cx, double cy, double radius, double startDeg, double endDeg,
                            boolean full, double[] m, int color) {
            if (radius <= 0) return;
            double a0 = Math.toRadians(startDeg);
            double a1 = Math.toRadians(endDeg);
            if (full) {
                a0 = 0;
                a1 = 2 * Math.PI;
            } else if (a1 <= a0) {
                a1 += 2 * Math.PI;
            }
            int steps = (int) Math.max(8, Math.ceil((a1 - a0) / ARC_STEP));
            List<double[]> pts = new ArrayList<>();
            int last = full ? steps - 1 : steps;
            for (int k = 0; k <= last; k++) {
                double a = a0 + (a1 - a0) * k / steps;
                pts.add(new double[]{cx + radius * Math.cos(a), cy + radius * Math.sin(a)});
            }
            addPoly(pts, full, m, color);
        }

        private void addEllipse(Raw r, double[] m, int color) {
            double cx = r.num(10, 0);
            double cy = r.num(20, 0);
            double mx = r.num(11, 1);
            double my = r.num(21, 0);
            double ratio = r.num(40, 1);
            double t0 = r.num(41, 0);
            double t1 = r.num(42, 2 * Math.PI);
            if (t1 <= t0) t1 += 2 * Math.PI;
            boolean full = Math.abs((t1 - t0) - 2 * Math.PI) < 1e-6;

            double nx = -my * ratio;
            double ny = mx * ratio;

            int steps = (int) Math.max(12, Math.ceil((t1 - t0) / ARC_STEP));
            int last = full ? steps - 1 : steps;
            List<double[]> pts = new ArrayList<>();
            for (int k = 0; k <= last; k++) {
                double t = t0 + (t1 - t0) * k / steps;
                pts.add(new double[]{
                        cx + Math.cos(t) * mx + Math.sin(t) * nx,
                        cy + Math.cos(t) * my + Math.sin(t) * ny});
            }
            addPoly(pts, full, m, color);
        }

        private void addSpline(Raw r, double[] m, int color) {
            List<double[]> pts = collectPoints(r, 11, 21);
            if (pts.size() < 2) pts = collectPoints(r, 10, 20);
            if (pts.size() < 2) return;
            boolean closed = (r.integer(70, 0) & 1) != 0;
            addPoly(pts, closed, m, color);
        }

        private List<double[]> collectPoints(Raw r, int xCode, int yCode) {
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i < r.codes.size(); i++) {
                int code = r.codes.get(i);
                try {
                    if (code == xCode) {
                        pts.add(new double[]{Double.parseDouble(r.values.get(i).trim()), 0});
                    } else if (code == yCode && !pts.isEmpty()) {
                        pts.get(pts.size() - 1)[1] = Double.parseDouble(r.values.get(i).trim());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return pts;
        }

        private void addText(Raw r, double[] m, int color, boolean mtext) {
            String s;
            if (mtext) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.codes.size(); i++) {
                    if (r.codes.get(i) == 3) sb.append(r.values.get(i));
                }
                sb.append(r.str(1, ""));
                s = cleanMText(sb.toString());
            } else {
                s = cleanText(r.str(1, ""));
            }
            if (s.trim().isEmpty()) return;

            double x = r.num(10, 0);
            double y = r.num(20, 0);
            double h = r.num(40, 2.5);
            if (h <= 0) h = 2.5;

            double rot;
            if (mtext && r.str(11, null) != null) {
                rot = Math.atan2(r.num(21, 0), r.num(11, 1));
            } else {
                rot = mtext ? r.num(50, 0) : Math.toRadians(r.num(50, 0));
            }

            double wx = m[0] * x + m[2] * y + m[4];
            double wy = m[1] * x + m[3] * y + m[5];
            double scale = Math.sqrt(Math.abs(m[0] * m[3] - m[1] * m[2]));
            if (scale <= 0) scale = 1;
            double wrot = rot + Math.atan2(m[1], m[0]);

            texts.add(new Txt(wx, wy, h * scale, wrot, s, color, mtext));
            include(wx, wy);
        }

        private String cleanText(String s) {
            return s.replaceAll("(?i)%%c", "Ø")
                    .replaceAll("(?i)%%d", "°")
                    .replaceAll("(?i)%%p", "±")
                    .replaceAll("(?i)%%[uo]", "");
        }

        private String cleanMText(String s) {
            s = s.replace("\\P", "\n").replace("\\~", " ");
            s = s.replaceAll("\\\\[A-Za-z][^;\\\\]*;", "");
            s = s.replaceAll("\\\\[LlOoKk]", "");
            s = s.replace("{", "").replace("}", "");
            s = s.replace("\\\\", "\\");
            return cleanText(s);
        }

        // ---------- Utilidades ----------

        private void addPoly(List<double[]> pts, boolean closed, double[] m, int color) {
            int n = pts.size();
            if (n < 2) return;
            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                double x = pts.get(i)[0];
                double y = pts.get(i)[1];
                xs[i] = m[0] * x + m[2] * y + m[4];
                ys[i] = m[1] * x + m[3] * y + m[5];
                include(xs[i], ys[i]);
            }
            polys.add(new Poly(xs, ys, closed, color));
        }

        private void include(double x, double y) {
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isInfinite(x) || Double.isInfinite(y)) return;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        private void finishBounds() {
            if (isEmpty() || minX > maxX || minY > maxY) {
                minX = minY = 0;
                maxX = maxY = 1;
                return;
            }
            double dx = maxX - minX;
            double dy = maxY - minY;
            if (dx < 1e-9) maxX = minX + Math.max(dy, 1.0);
            if (dy < 1e-9) maxY = minY + Math.max(dx, 1.0);
        }

        /** Composição de matrizes 2D afins: resultado = p aplicado depois de q. */
        private static double[] mul(double[] p, double[] q) {
            return new double[]{
                    p[0] * q[0] + p[2] * q[1],
                    p[1] * q[0] + p[3] * q[1],
                    p[0] * q[2] + p[2] * q[3],
                    p[1] * q[2] + p[3] * q[3],
                    p[0] * q[4] + p[2] * q[5] + p[4],
                    p[1] * q[4] + p[3] * q[5] + p[5]
            };
        }

        /** Converte índice de cor AutoCAD (ACI) para RGB visível em fundo branco. */
        private static int aciToRgb(int aci) {
            switch (aci) {
                case 1: return Color.rgb(255, 0, 0);
                case 2: return Color.rgb(200, 160, 0);
                case 3: return Color.rgb(0, 160, 0);
                case 4: return Color.rgb(0, 160, 160);
                case 5: return Color.rgb(0, 0, 255);
                case 6: return Color.rgb(192, 0, 192);
                case 7: return Color.BLACK;
                case 8: return Color.rgb(128, 128, 128);
                case 9: return Color.rgb(160, 160, 160);
                default: return Color.rgb(40, 40, 40);
            }
        }
    }
}