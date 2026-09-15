package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.api.GeminiNutritionService
import com.example.data.dao.NutritionScanDao
import com.example.data.model.NutritionScan
import kotlinx.coroutines.flow.Flow

class NutritionRepository(
    private val scanDao: NutritionScanDao,
    private val geminiService: GeminiNutritionService = GeminiNutritionService()
) {
    val allScans: Flow<List<NutritionScan>> = scanDao.getAllScans()

    suspend fun analyzeFood(bitmap: Bitmap): Result<NutritionScan> {
        val result = geminiService.analyzeFoodImage(bitmap)
        if (result.isSuccess) {
            val scan = result.getOrThrow()
            val insertedId = scanDao.insertScan(scan)
            return Result.success(scan.copy(id = insertedId))
        }
        return result
    }

    suspend fun saveSampleScan(scan: NutritionScan): Long {
        return scanDao.insertScan(scan)
    }

    suspend fun deleteScan(id: Long) {
        scanDao.deleteScan(id)
    }

    suspend fun clearHistory() {
        scanDao.clearAllScans()
    }
}
