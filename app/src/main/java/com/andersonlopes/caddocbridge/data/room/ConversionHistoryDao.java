package com.andersonlopes.caddocbridge.data.room;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.andersonlopes.caddocbridge.model.ConversionHistory;

import java.util.List;

@Dao
public interface ConversionHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ConversionHistory history);

    @Delete
    int delete(ConversionHistory history);

    @Query("DELETE FROM conversion_history WHERE id = :id")
    int deleteById(long id);

    @Query("DELETE FROM conversion_history WHERE id IN (:ids)")
    int deleteByIds(List<Long> ids);

    @Query("DELETE FROM conversion_history WHERE file_path IN (:paths)")
    int deleteByPaths(List<String> paths);

    @Query("DELETE FROM conversion_history")
    int deleteAll();

    @Query("SELECT * FROM conversion_history ORDER BY created_at DESC")
    LiveData<List<ConversionHistory>> observeAll();

    @Query("SELECT * FROM conversion_history WHERE format = :format ORDER BY created_at DESC")
    LiveData<List<ConversionHistory>> observeByFormat(String format);

    @Query("SELECT * FROM conversion_history WHERE file_name LIKE '%' || :query || '%' ORDER BY created_at DESC")
    LiveData<List<ConversionHistory>> search(String query);

    @Query("SELECT * FROM conversion_history WHERE id = :id LIMIT 1")
    ConversionHistory getById(long id);

    @Query("SELECT * FROM conversion_history WHERE file_path = :path LIMIT 1")
    ConversionHistory getByPath(String path);

    @Query("SELECT * FROM conversion_history ORDER BY created_at DESC")
    List<ConversionHistory> getAllSync();

    @Query("SELECT COUNT(*) FROM conversion_history")
    int count();
}