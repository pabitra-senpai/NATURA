package com.natura.camera.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natura.camera.camera.CameraMode
import com.natura.camera.camera.ZoomLensHelper
import com.natura.camera.ui.CameraViewModel
import com.natura.camera.ui.components.*
import com.natura.camera.ui.theme.*

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isProMode = (uiState.settings.cameraMode == CameraMode.PRO)

    val zoomRange = uiState.capabilities?.zoomRatioRange ?: android.util.Range(1f, 1f)
    val availableZoomPresets = ZoomLensHelper.getAvailablePresets(zoomRange)

    Scaffold(
        topBar = {
            TopBar(
                flashMode = uiState.settings.flashMode,
                isFlashSupported = uiState.capabilities?.isFlashAvailable ?: false,
                isRecording = uiState.isRecording,
                recordingSeconds = uiState.recordingSeconds,
                onFlashClick = {
                    val next = when (uiState.settings.flashMode) {
                        com.natura.camera.camera.FlashMode.OFF -> com.natura.camera.camera.FlashMode.ON
                        com.natura.camera.camera.FlashMode.ON -> com.natura.camera.camera.FlashMode.TORCH
                        com.natura.camera.camera.FlashMode.TORCH -> com.natura.camera.camera.FlashMode.OFF
                    }
                    viewModel.setFlashMode(next)
                },
                onSettingsClick = { viewModel.openSettings() }
            )
        },
        containerColor = NaturaBlack,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Viewfinder
                Viewfinder(
                    aspectRatio = uiState.settings.aspectRatio,
                    isProMode = isProMode,
                    gridEnabled = uiState.settings.gridEnabled,
                    rollDegrees = uiState.rollDegrees,
                    isLevel = uiState.isLevel,
                    zoomRatio = uiState.zoomRatio,
                    availableZoomPresets = availableZoomPresets,
                    outputFormat = uiState.settings.outputFormat,
                    readout = uiState.readout,
                    isShutterFlashing = uiState.isShutterFlashing,
                    timerCountdown = uiState.timerCountdown,
                    focusTapPoint = uiState.focusTapPoint,
                    isFocusBoxVisible = uiState.isFocusBoxVisible,
                    isAeAfLocked = uiState.isAeAfLocked,
                    isFrontCamera = uiState.capabilities?.isFrontCamera ?: false,
                    histogramBins = uiState.histogramBins,
                    onSurfaceTextureAvailable = { texture ->
                        viewModel.onSurfaceTextureAvailable(texture)
                    },
                    onFocusTap = { nx, ny -> viewModel.onFocusTap(nx, ny) },
                    onFocusLongPress = { nx, ny -> viewModel.onFocusLongPress(nx, ny) },
                    onZoomRatioChanged = { ratio -> viewModel.setZoomRatio(ratio) },
                    onHistogramBitmapAvailable = { bmp -> viewModel.updateHistogram(bmp) }
                )

                // 2. Middle Section: Pro Controls (if in PRO mode)
                if (isProMode) {
                    ProPanel(
                        selectedParam = uiState.selectedProParam,
                        manualIso = uiState.manualIso,
                        manualShutterNanos = uiState.manualShutterNanos,
                        manualEv = uiState.manualEv,
                        manualKelvin = uiState.manualKelvin,
                        manualFocusDistance = uiState.manualFocusDistance,
                        isAeAfLocked = uiState.isAeAfLocked,
                        outputFormat = uiState.settings.outputFormat,
                        capabilities = uiState.capabilities,
                        onParamSelected = { p -> viewModel.selectProParam(p) },
                        onIsoChanged = { iso -> viewModel.updateManualIso(iso) },
                        onShutterChanged = { s -> viewModel.updateManualShutter(s) },
                        onEvChanged = { ev -> viewModel.updateManualEv(ev) },
                        onKelvinChanged = { k -> viewModel.updateManualKelvin(k) },
                        onFocusChanged = { f -> viewModel.updateManualFocus(f) },
                        onAutoReset = { viewModel.resetAuto() },
                        onAeAfLockToggle = { viewModel.toggleAeAfLock() },
                        onRawToggle = { viewModel.toggleRawFormat() },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // 3. Mode Selector
                ModeSelector(
                    currentMode = uiState.settings.cameraMode,
                    isRawHardwareSupported = uiState.capabilities?.isRawSupported ?: false,
                    onModeSelected = { mode -> viewModel.setCameraMode(mode) }
                )

                // 4. Shutter Row
                ShutterRow(
                    cameraMode = uiState.settings.cameraMode,
                    isRecording = uiState.isRecording,
                    latestMedia = uiState.latestMedia,
                    isProMode = isProMode,
                    onShutterClick = { viewModel.onShutterPressed() },
                    onGalleryClick = { viewModel.openGallery() },
                    onFlipClick = { viewModel.switchCamera() }
                )
            }

            // Error Overlay: full-screen black message with single amber button
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NaturaBlack)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "Camera Notice",
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = NaturaTextPrimary
                        )
                        Text(
                            text = uiState.errorMessage ?: "",
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            color = NaturaTextMuted
                        )
                        Button(
                            onClick = { viewModel.clearError() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NaturaAmber,
                                contentColor = NaturaBlack
                            ),
                            shape = RoundedCornerShape(100),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("dismiss_error_button")
                        ) {
                            Text(
                                text = "Dismiss",
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // In-app Gallery Viewer
            if (uiState.isGalleryOpen) {
                GalleryViewer(
                    items = uiState.allMedia,
                    onClose = { viewModel.closeGallery() },
                    onItemDeleted = { viewModel.refreshGallery() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Settings Screen
            if (uiState.isSettingsOpen) {
                SettingsScreen(
                    settings = uiState.settings,
                    capabilities = uiState.capabilities,
                    onUpdateSettings = { transform -> viewModel.updateSettings(transform) },
                    onBack = { viewModel.closeSettings() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
