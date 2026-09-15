package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.model.NutritionScan
import com.example.data.repository.NutritionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    SCANNER,
    RESULT,
    HISTORY
}

data class ScanUiState(
    val isAnalyzing: Boolean = false,
    val selectedScan: NutritionScan? = null,
    val capturedBitmap: Bitmap? = null,
    val currentTab: AppTab = AppTab.SCANNER,
    val errorMessage: String? = null,
    val isFlashOn: Boolean = false,
    val isFrontCamera: Boolean = false,
    val hasApiKey: Boolean = false
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NutritionRepository

    val historyList: StateFlow<List<NutritionScan>>

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NutritionRepository(database.nutritionScanDao())
        historyList = repository.allScans.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        val hasKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"
        _uiState.update { it.copy(hasApiKey = hasKey) }
    }

    fun analyzeBitmap(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                capturedBitmap = bitmap,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = repository.analyzeFood(bitmap)
            result.fold(
                onSuccess = { scan ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            selectedScan = scan,
                            currentTab = AppTab.RESULT
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            errorMessage = error.localizedMessage ?: "খাদ্য বিশ্লেষণে ত্রুটি দেখা দিয়েছে।"
                        )
                    }
                }
            )
        }
    }

    fun selectSampleFood(sample: NutritionScan) {
        viewModelScope.launch {
            val id = repository.saveSampleScan(sample)
            val saved = sample.copy(id = id)
            _uiState.update {
                it.copy(
                    selectedScan = saved,
                    currentTab = AppTab.RESULT,
                    errorMessage = null
                )
            }
        }
    }

    fun selectScan(scan: NutritionScan) {
        _uiState.update {
            it.copy(
                selectedScan = scan,
                currentTab = AppTab.RESULT
            )
        }
    }

    fun deleteScan(id: Long) {
        viewModelScope.launch {
            repository.deleteScan(id)
            if (_uiState.value.selectedScan?.id == id) {
                _uiState.update { it.copy(selectedScan = null, currentTab = AppTab.SCANNER) }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun switchTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun toggleFlash() {
        _uiState.update { it.copy(isFlashOn = !it.isFlashOn) }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
