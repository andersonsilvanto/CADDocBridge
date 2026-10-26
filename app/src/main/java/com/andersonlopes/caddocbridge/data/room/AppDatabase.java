package com.andersonlopes.caddocbridge.data.room;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.andersonlopes.caddocbridge.model.ConversionHistory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {ConversionHistory.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "caddocbridge.db";
    private static volatile AppDatabase INSTANCE;

    public static final ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(2);

    public abstract ConversionHistoryDao conversionHistoryDao();

    @NonNull
    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}