package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.NutritionScan
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionScanDao {
    @Query("SELECT * FROM nutrition_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<NutritionScan>>

    @Query("SELECT * FROM nutrition_scans WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: Long): NutritionScan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: NutritionScan): Long

    @Query("DELETE FROM nutrition_scans WHERE id = :id")
    suspend fun deleteScan(id: Long)

    @Query("DELETE FROM nutrition_scans")
    suspend fun clearAllScans()
}
