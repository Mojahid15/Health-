package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CameraScannerView
import com.example.ui.components.NutritionResultView
import com.example.ui.components.ScanHistoryView
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintLight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.ScanViewModel

class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = scanViewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyList.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(bottom = if (uiState.currentTab == AppTab.SCANNER) 90.dp else 16.dp)
            )
        },
        bottomBar = {
            // Only show bottom navigation when on Scanner or History (not while viewing full result)
            AnimatedVisibility(
                visible = uiState.currentTab != AppTab.RESULT,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF0F172A),
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.SCANNER,
                        onClick = { viewModel.switchTab(AppTab.SCANNER) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scanner"
                            )
                        },
                        label = { Text("স্ক্যান করুন") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldDark,
                            selectedTextColor = MintLight,
                            indicatorColor = MintLight,
                            unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("nav_tab_scanner")
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.HISTORY,
                        onClick = { viewModel.switchTab(AppTab.HISTORY) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History"
                            )
                        },
                        label = { Text("হিস্ট্রি (${history.size})") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldDark,
                            selectedTextColor = MintLight,
                            indicatorColor = MintLight,
                            unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("nav_tab_history")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                AppTab.SCANNER -> {
                    CameraScannerView(
                        isAnalyzing = uiState.isAnalyzing,
                        hasApiKey = uiState.hasApiKey,
                        onImageCaptured = { bitmap ->
                            viewModel.analyzeBitmap(bitmap)
                        },
                        onSampleSelected = { sample ->
                            viewModel.selectSampleFood(sample)
                        }
                    )
                }
                AppTab.RESULT -> {
                    val scan = uiState.selectedScan
                    if (scan != null) {
                        NutritionResultView(
                            scan = scan,
                            capturedBitmap = uiState.capturedBitmap,
                            onBackToScanner = { viewModel.switchTab(AppTab.SCANNER) },
                            onViewHistory = { viewModel.switchTab(AppTab.HISTORY) }
                        )
                    } else {
                        // Fallback if no scan is selected
                        LaunchedEffect(Unit) {
                            viewModel.switchTab(AppTab.SCANNER)
                        }
                    }
                }
                AppTab.HISTORY -> {
                    ScanHistoryView(
                        scans = history,
                        onScanSelected = { scan ->
                            viewModel.selectScan(scan)
                        },
                        onDeleteScan = { id ->
                            viewModel.deleteScan(id)
                        },
                        onClearAll = {
                            viewModel.clearHistory()
                        },
                        onBackToScanner = {
                            viewModel.switchTab(AppTab.SCANNER)
                        }
                    )
                }
            }
        }
    }
}
