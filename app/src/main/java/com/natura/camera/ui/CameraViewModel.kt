package com.natura.camera.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.natura.camera.camera.*
import com.natura.camera.data.SettingsRepository
import com.natura.camera.util.OrientationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val settings: NaturaSettings = NaturaSettings(),
    val capabilities: CameraCapabilities? = null,
    val readout: CaptureReadout = CaptureReadout(),
    val rollDegrees: Float = 0f,
    val isLevel: Boolean = true,
    val zoomRatio: Float = 1.0f,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val timerCountdown: Int? = null,
    val isShutterFlashing: Boolean = false,
    val focusTapPoint: Offset? = null,
    val isFocusBoxVisible: Boolean = false,
    val isAeAfLocked: Boolean = false,
    val selectedProParam: ProParameter = ProParameter.ISO,
    val manualIso: Int? = null,
    val manualShutterNanos: Long? = null,
    val manualEv: Int = 0,
    val manualKelvin: Int = 5500,
    val manualFocusDistance: Float? = null,
    val latestMedia: NaturaMediaItem? = null,
    val allMedia: List<NaturaMediaItem> = emptyList(),
    val isGalleryOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val errorMessage: String? = null,
    val histogramBins: FloatArray = FloatArray(64) { 0f }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraUiState) return false
        return settings == other.settings &&
                capabilities == other.capabilities &&
                readout == other.readout &&
                rollDegrees == other.rollDegrees &&
                isLevel == other.isLevel &&
                zoomRatio == other.zoomRatio &&
                isRecording == other.isRecording &&
                recordingSeconds == other.recordingSeconds &&
                timerCountdown == other.timerCountdown &&
                isShutterFlashing == other.isShutterFlashing &&
                focusTapPoint == other.focusTapPoint &&
                isFocusBoxVisible == other.isFocusBoxVisible &&
                isAeAfLocked == other.isAeAfLocked &&
                selectedProParam == other.selectedProParam &&
                manualIso == other.manualIso &&
                manualShutterNanos == other.manualShutterNanos &&
                manualEv == other.manualEv &&
                manualKelvin == other.manualKelvin &&
                manualFocusDistance == other.manualFocusDistance &&
                latestMedia == other.latestMedia &&
                allMedia == other.allMedia &&
                isGalleryOpen == other.isGalleryOpen &&
                isSettingsOpen == other.isSettingsOpen &&
                errorMessage == other.errorMessage &&
                histogramBins.contentEquals(other.histogramBins)
    }

    override fun hashCode(): Int {
        var result = settings.hashCode()
        result = 31 * result + (capabilities?.hashCode() ?: 0)
        result = 31 * result + readout.hashCode()
        result = 31 * result + rollDegrees.hashCode()
        result = 31 * result + isLevel.hashCode()
        result = 31 * result + zoomRatio.hashCode()
        result = 31 * result + isRecording.hashCode()
        result = 31 * result + recordingSeconds
        result = 31 * result + (timerCountdown ?: 0)
        result = 31 * result + isShutterFlashing.hashCode()
        result = 31 * result + (focusTapPoint?.hashCode() ?: 0)
        result = 31 * result + isFocusBoxVisible.hashCode()
        result = 31 * result + isAeAfLocked.hashCode()
        result = 31 * result + selectedProParam.hashCode()
        result = 31 * result + (manualIso ?: 0)
        result = 31 * result + (manualShutterNanos?.hashCode() ?: 0)
        result = 31 * result + manualEv
        result = 31 * result + manualKelvin
        result = 31 * result + (manualFocusDistance?.hashCode() ?: 0)
        result = 31 * result + (latestMedia?.hashCode() ?: 0)
        result = 31 * result + allMedia.hashCode()
        result = 31 * result + isGalleryOpen.hashCode()
        result = 31 * result + isSettingsOpen.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + histogramBins.contentHashCode()
        return result
    }
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val vibrator = application.getSystemService(Vibrator::class.java)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var activeSurfaceTexture: SurfaceTexture? = null
    private var recordingTimerJob: Job? = null
    private var focusBoxFadeJob: Job? = null
    private var countdownJob: Job? = null

    val orientationManager = OrientationManager(application) { rollDegrees, isLevel ->
        _uiState.update { it.copy(rollDegrees = rollDegrees, isLevel = isLevel) }
    }

    val cameraController = CameraController(
        context = application,
        orientationManager = orientationManager,
        coroutineScope = viewModelScope,
        onReadoutUpdated = { readout ->
            _uiState.update { it.copy(readout = readout) }
        },
        onCaptureComplete = { jpegUri, dngUri ->
            refreshGallery()
        },
        onError = { error ->
            _uiState.update { it.copy(errorMessage = error) }
        }
    )

    init {
        orientationManager.start()
        refreshGallery()

        // Observe DataStore settings
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { savedSettings ->
                _uiState.update { it.copy(settings = savedSettings) }
                cameraController.applySettings(savedSettings, activeSurfaceTexture)
            }
        }
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture) {
        activeSurfaceTexture = surfaceTexture
        if (!cameraController.isCameraOpen) {
            val currentSettings = _uiState.value.settings
            cameraController.openCamera(surfaceTexture, currentSettings)
            _uiState.update { it.copy(capabilities = cameraController.currentCapabilities) }
        }
    }

    fun onResume() {
        orientationManager.start()
        activeSurfaceTexture?.let { texture ->
            if (!cameraController.isCameraOpen) {
                cameraController.openCamera(texture, _uiState.value.settings)
                _uiState.update { it.copy(capabilities = cameraController.currentCapabilities) }
            }
        }
        refreshGallery()
    }

    fun onPause() {
        orientationManager.stop()
        if (_uiState.value.isRecording) {
            stopVideoRecording()
        }
        cameraController.closeCamera()
    }

    override fun onCleared() {
        super.onCleared()
        orientationManager.stop()
        cameraController.release()
    }

    fun triggerHapticFeedback() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    fun onShutterPressed() {
        if (_uiState.value.isRecording) {
            stopVideoRecording()
            return
        }

        if (_uiState.value.settings.cameraMode == CameraMode.VIDEO) {
            startVideoRecording()
            return
        }

        val timerSeconds = _uiState.value.settings.timerSetting.seconds
        if (timerSeconds > 0) {
            startTimerCountdown(timerSeconds) {
                executePhotoCapture()
            }
        } else {
            executePhotoCapture()
        }
    }

    private fun startTimerCountdown(seconds: Int, onComplete: () -> Unit) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _uiState.update { it.copy(timerCountdown = i) }
                triggerHapticFeedback()
                delay(1000)
            }
            _uiState.update { it.copy(timerCountdown = null) }
            onComplete()
        }
    }

    private fun executePhotoCapture() {
        // Trigger 80ms viewfinder flash
        viewModelScope.launch {
            _uiState.update { it.copy(isShutterFlashing = true) }
            delay(80)
            _uiState.update { it.copy(isShutterFlashing = false) }
        }

        cameraController.takePhoto {
            triggerHapticFeedback()
        }
    }

    private fun startVideoRecording() {
        val texture = activeSurfaceTexture ?: return
        triggerHapticFeedback()
        cameraController.startVideoRecording(texture)
        _uiState.update { it.copy(isRecording = true, recordingSeconds = 0) }

        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                delay(1000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    private fun stopVideoRecording() {
        val texture = activeSurfaceTexture ?: return
        triggerHapticFeedback()
        recordingTimerJob?.cancel()
        cameraController.stopVideoRecording(texture)
        _uiState.update { it.copy(isRecording = false, recordingSeconds = 0) }
        refreshGallery()
    }

    fun setCameraMode(mode: CameraMode) {
        if (_uiState.value.isRecording) return
        triggerHapticFeedback()
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(cameraMode = mode) }
        }
    }

    fun setFlashMode(flashMode: FlashMode) {
        triggerHapticFeedback()
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(flashMode = flashMode) }
        }
    }

    fun setZoomRatio(ratio: Float) {
        _uiState.update { it.copy(zoomRatio = ratio) }
        cameraController.setZoomRatio(ratio)
    }

    fun onFocusTap(normalizedX: Float, normalizedY: Float) {
        _uiState.update {
            it.copy(
                focusTapPoint = Offset(normalizedX, normalizedY),
                isFocusBoxVisible = true
            )
        }
        cameraController.focusOnPoint(normalizedX, normalizedY, isLongPressLock = false)
        focusBoxFadeJob?.cancel()
        focusBoxFadeJob = viewModelScope.launch {
            delay(1500)
            _uiState.update { it.copy(isFocusBoxVisible = false) }
        }
    }

    fun onFocusLongPress(normalizedX: Float, normalizedY: Float) {
        triggerHapticFeedback()
        _uiState.update {
            it.copy(
                focusTapPoint = Offset(normalizedX, normalizedY),
                isFocusBoxVisible = true,
                isAeAfLocked = true
            )
        }
        cameraController.focusOnPoint(normalizedX, normalizedY, isLongPressLock = true)
    }

    fun toggleAeAfLock() {
        triggerHapticFeedback()
        val newLocked = !_uiState.value.isAeAfLocked
        cameraController.isAeAfLocked = newLocked
        cameraController.updateRepeatingRequest()
        _uiState.update { it.copy(isAeAfLocked = newLocked) }
    }

    fun resetAuto() {
        triggerHapticFeedback()
        cameraController.resetAuto()
        _uiState.update {
            it.copy(
                isAeAfLocked = false,
                manualIso = null,
                manualShutterNanos = null,
                manualEv = 0,
                manualKelvin = 5500,
                manualFocusDistance = null,
                isFocusBoxVisible = false
            )
        }
    }

    fun toggleRawFormat() {
        triggerHapticFeedback()
        val currentFormat = _uiState.value.settings.outputFormat
        val newFormat = if (currentFormat == OutputFormat.JPEG) OutputFormat.JPEG_DNG else OutputFormat.JPEG
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(outputFormat = newFormat) }
        }
    }

    fun selectProParam(param: ProParameter) {
        triggerHapticFeedback()
        _uiState.update { it.copy(selectedProParam = param) }
    }

    fun updateManualIso(iso: Int?) {
        cameraController.manualIso = iso
        cameraController.updateRepeatingRequest()
        _uiState.update { it.copy(manualIso = iso) }
    }

    fun updateManualShutter(nanos: Long?) {
        cameraController.manualShutterNanos = nanos
        cameraController.updateRepeatingRequest()
        _uiState.update { it.copy(manualShutterNanos = nanos) }
    }

    fun updateManualEv(evSteps: Int) {
        cameraController.manualEv = evSteps
        cameraController.updateRepeatingRequest()
        _uiState.update { it.copy(manualEv = evSteps) }
    }

    fun updateManualKelvin(kelvin: Int) {
        cameraController.manualKelvin = kelvin
        cameraController.updateRepeatingRequest()
        _uiState.update { it.copy(manualKelvin = kelvin) }
    }

    fun updateManualFocus(distance: Float?) {
        cameraController.manualFocusDistance = distance
        cameraController.updateRepeatingRequest()
        _uiState.update { it.copy(manualFocusDistance = distance) }
    }

    fun switchCamera() {
        triggerHapticFeedback()
        cameraController.switchCamera(activeSurfaceTexture)
        _uiState.update { it.copy(capabilities = cameraController.currentCapabilities) }
    }

    fun updateHistogram(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rawBins = FloatArray(64)
        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // Rec. 709 luminance
            val lum = ((0.2126f * r) + (0.7152f * g) + (0.0722f * b)).toInt().coerceIn(0, 255)
            val bin = lum / 4 // 256 / 4 = 64 bins
            rawBins[bin] += 1f
        }

        val maxVal = rawBins.maxOrNull() ?: 1f
        if (maxVal > 0f) {
            for (i in rawBins.indices) {
                rawBins[i] /= maxVal
            }
        }
        _uiState.update { it.copy(histogramBins = rawBins) }
    }

    fun openGallery() {
        triggerHapticFeedback()
        refreshGallery()
        _uiState.update { it.copy(isGalleryOpen = true) }
    }

    fun closeGallery() {
        _uiState.update { it.copy(isGalleryOpen = false) }
    }

    fun openSettings() {
        triggerHapticFeedback()
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateSettings(transform: (NaturaSettings) -> NaturaSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
        }
    }

    fun refreshGallery() {
        viewModelScope.launch {
            val items = MediaStoreSaver.queryNaturaMedia(getApplication())
            _uiState.update {
                it.copy(
                    allMedia = items,
                    latestMedia = items.firstOrNull()
                )
            }
        }
    }
}
