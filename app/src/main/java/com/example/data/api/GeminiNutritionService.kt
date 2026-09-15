package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.NutritionScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiNutritionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeFoodImage(bitmap: Bitmap): Result<NutritionScan> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        val isKeyConfigured = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

        if (!isKeyConfigured) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API Key টি সেট করা হয়নি। অনুগ্রহ করে AI Studio-এর Secrets প্যানেল থেকে GEMINI_API_KEY যোগ করুন।")
            )
        }

        try {
            // Compress & resize bitmap for efficient transmission
            val scaledBitmap = scaleBitmapDown(bitmap, 1024)
            val base64Image = bitmapToBase64(scaledBitmap)
            val thumbnailBase64 = bitmapToBase64(scaleBitmapDown(bitmap, 300))

            val promptText = """
                You are a certified clinical nutritionist and expert food recognition AI.
                Analyze the food or beverage in this image accurately and provide complete nutritional details.
                Return ONLY valid JSON matching this schema:
                {
                  "foodNameBn": "খাবারের সঠিক নাম বাংলায় (যেমন: ডিম পোচ, কাঁচা সালাদ, বিরিয়ানি)",
                  "foodNameEn": "Accurate English food name (e.g., Poached Egg, Garden Salad)",
                  "servingSize": "আনুমানিক পরিবেশন আকার (যেমন: ১ প্লেট (২৫০ গ্রাম) / 1 piece (120g))",
                  "calories": 250.0,
                  "protein": 14.5,
                  "totalFat": 9.2,
                  "saturatedFat": 2.5,
                  "carbs": 28.0,
                  "fiber": 3.8,
                  "sugar": 4.2,
                  "sodium": 420.0,
                  "potassium": 310.0,
                  "healthScore": 85,
                  "healthVerdictBn": "সংক্ষিপ্ত স্বাস্থ্য মতামত (যেমন: অত্যন্ত পুষ্টিকর ও সুষম)",
                  "healthTipBn": "বাংলায় স্বাস্থ্য ও খাদ্যাভ্যাস পরামর্শ (যেমন: এই খাবারে উচ্চমানের প্রোটিন রয়েছে, যা সারাদিনের শক্তি যোগায়)",
                  "dietaryTags": ["প্রোটিন সমৃদ্ধ", "ফাইবার উৎস", "কম শর্করা"]
                }
                Values for protein, totalFat, saturatedFat, carbs, fiber, sugar must be in grams (Double).
                Calories in kcal (Double). Sodium and potassium in milligrams (Double).
                healthScore must be an integer between 1 and 100.
                If the image does not depict any food or drink, provide best reasonable analysis or state so in foodNameBn.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject().apply {
                    val parts = JSONArray()
                    parts.put(JSONObject().apply {
                        put("text", promptText)
                    })
                    parts.put(JSONObject().apply {
                        val inlineData = JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        }
                        put("inlineData", inlineData)
                    })
                    put("parts", parts)
                }
                contents.put(contentObj)
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", generationConfig)
            }

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                Log.e("GeminiNutritionService", "API Error ${response.code}: $responseBody")
                return@withContext Result.failure(
                    Exception("গুগল এআই সার্ভার রেসপন্স করেনি (Code: ${response.code})")
                )
            }

            val parsedScan = parseGeminiResponse(responseBody, thumbnailBase64)
            Result.success(parsedScan)
        } catch (e: Exception) {
            Log.e("GeminiNutritionService", "Analysis exception", e)
            Result.failure(e)
        }
    }

    private fun parseGeminiResponse(responseBody: String, thumbnailBase64: String): NutritionScan {
        val root = JSONObject(responseBody)
        val candidates = root.getJSONArray("candidates")
        if (candidates.length() == 0) {
            throw IllegalStateException("এআই কোনো ফলাফল দিতে পারেনি")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val text = parts.getJSONObject(0).getString("text")

        // Clean any code markdown block if returned
        val cleanJson = text
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val json = JSONObject(cleanJson)

        val foodNameBn = json.optString("foodNameBn", "অজ্ঞাত খাদ্য")
        val foodNameEn = json.optString("foodNameEn", "Identified Food")
        val servingSize = json.optString("servingSize", "১টি সাধারণ পরিবেশন (১০০ গ্রাম)")
        val calories = json.optDouble("calories", 150.0)
        val protein = json.optDouble("protein", 5.0)
        val totalFat = json.optDouble("totalFat", 3.0)
        val saturatedFat = json.optDouble("saturatedFat", 1.0)
        val carbs = json.optDouble("carbs", 20.0)
        val fiber = json.optDouble("fiber", 2.0)
        val sugar = json.optDouble("sugar", 2.0)
        val sodium = json.optDouble("sodium", 50.0)
        val potassium = json.optDouble("potassium", 150.0)
        val healthScore = json.optInt("healthScore", 75)
        val healthVerdictBn = json.optString("healthVerdictBn", "পুষ্টিকর খাদ্য")
        val healthTipBn = json.optString("healthTipBn", "পরিমিত মাত্রায় গ্রহণ করুন ও স্বাস্থ্যকর খাদ্যাভ্যাস বজায় রাখুন।")

        val tagsArray = json.optJSONArray("dietaryTags")
        val tagsList = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tagsList.add(tagsArray.getString(i))
            }
        }
        val dietaryTagsCsv = if (tagsList.isEmpty()) "পুষ্টিকর খাবার" else tagsList.joinToString(", ")

        return NutritionScan(
            foodNameBn = foodNameBn,
            foodNameEn = foodNameEn,
            servingSize = servingSize,
            calories = calories,
            protein = protein,
            totalFat = totalFat,
            saturatedFat = saturatedFat,
            carbs = carbs,
            fiber = fiber,
            sugar = sugar,
            sodium = sodium,
            potassium = potassium,
            healthScore = healthScore.coerceIn(1, 100),
            healthVerdictBn = healthVerdictBn,
            healthTipBn = healthTipBn,
            dietaryTagsCsv = dietaryTagsCsv,
            imageBase64 = thumbnailBase64,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
