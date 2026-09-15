package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nutrition_scans")
data class NutritionScan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val foodNameBn: String,
    val foodNameEn: String,
    val servingSize: String,
    val calories: Double,
    val protein: Double,
    val totalFat: Double,
    val saturatedFat: Double,
    val carbs: Double,
    val fiber: Double,
    val sugar: Double,
    val sodium: Double,
    val potassium: Double,
    val healthScore: Int,
    val healthVerdictBn: String,
    val healthTipBn: String,
    val dietaryTagsCsv: String,
    val imageBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
