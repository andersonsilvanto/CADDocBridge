package com.andersonlopes.caddocbridge.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Registro persistente de uma conversão realizada (Histórico - Seção 6 do plano).
 * Armazena nome, data, tamanho e formato, além do caminho para reabrir/reexportar.
 */
@Entity(
        tableName = "conversion_history",
        indices = {
                @Index(value = {"created_at"}),
                @Index(value = {"format"})
        }
)
public class ConversionHistory {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    /** Nome do arquivo gerado, ex.: "planta_terreo.dxf". */
    @NonNull
    @ColumnInfo(name = "file_name")
    private String fileName;

    /** Caminho absoluto do arquivo gerado (para reabrir/reexportar). */
    @Nullable
    @ColumnInfo(name = "file_path")
    private String filePath;

    /** Formato de destino em maiúsculas, ex.: "PDF", "DXF", "XLSX", "ZIP". */
    @NonNull
    @ColumnInfo(name = "format")
    private String format;

    /** Tamanho do arquivo em bytes. */
    @ColumnInfo(name = "size_bytes")
    private long sizeBytes;

    /** Data/hora da conversão em milissegundos (epoch UTC). */
    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** Construtor usado pelo Room. */
    public ConversionHistory(@NonNull String fileName,
                             @Nullable String filePath,
                             @NonNull String format,
                             long sizeBytes,
                             long createdAt) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.format = format;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    /** Construtor de conveniência: usa o horário atual. */
    @Ignore
    public ConversionHistory(@NonNull String fileName,
                             @Nullable String filePath,
                             @NonNull String format,
                             long sizeBytes) {
        this(fileName, filePath, format, sizeBytes, System.currentTimeMillis());
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    public void setFileName(@NonNull String fileName) {
        this.fileName = fileName;
    }

    @Nullable
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(@Nullable String filePath) {
        this.filePath = filePath;
    }

    @NonNull
    public String getFormat() {
        return format;
    }

    public void setFormat(@NonNull String format) {
        this.format = format;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}