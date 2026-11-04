package com.natura.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.natura.camera.util.OrientationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Low-level Camera2 Controller implementing Natura's core philosophy:
 * "Ask the camera pipeline for the LEAST processing each device supports."
 */
class CameraController(
    private val context: Context,
    private val orientationManager: OrientationManager,
    private val coroutineScope: CoroutineScope,
    private val onReadoutUpdated: (CaptureReadout) -> Unit,
    private val onCaptureComplete: (jpegUri: android.net.Uri?, dngUri: android.net.Uri?) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "NaturaCameraController"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var jpegImageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFileDescriptor: ParcelFileDescriptor? = null
    private var currentRecordingUri: android.net.Uri? = null

    var currentCameraId: String = "0"
        private set

    var currentCapabilities: CameraCapabilities? = null
        private set

    private var currentSettings: NaturaSettings = NaturaSettings()
    private var currentZoomRatio: Float = 1.0f
    private var isRecordingVideo: Boolean = false

    val isCameraOpen: Boolean
        get() = cameraDevice != null

    private var isOpeningCamera = false
    private var isCreatingSession = false
    private var activeSurfaceTexture: SurfaceTexture? = null

    // Manual Pro adjustments
    var manualIso: Int? = null
    var manualShutterNanos: Long? = null
    var manualEv: Int = 0
    var manualKelvin: Int = 5500
    var manualFocusDistance: Float? = null
    var isAeAfLocked: Boolean = false

    // Tap to focus regions
    private var activeAfRegion: MeteringRectangle? = null
    private var activeAeRegion: MeteringRectangle? = null

    // For pairing JPEG + DNG by timestamp
    private val pendingDngImages = ConcurrentHashMap<Long, Image>()
    private val pendingCaptureResults = ConcurrentHashMap<Long, CaptureResult>()
    private var activeBaseName: String = ""

    init {
        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("NaturaCameraBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    /**
     * Enumerates available camera IDs and picks the best back camera by default.
     */
    fun findBestCameraId(preferFacing: Int = CameraCharacteristics.LENS_FACING_BACK): String {
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == preferFacing) {
                    return id
                }
            }
            return cameraIds.firstOrNull() ?: "0"
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating camera IDs", e)
            return "0"
        }
    }

    /**
     * Switches between front and back camera.
     */
    fun switchCamera(surfaceTexture: SurfaceTexture?) {
        val targetFacing = if (currentCapabilities?.isFrontCamera == true) {
            CameraCharacteristics.LENS_FACING_BACK
        } else {
            CameraCharacteristics.LENS_FACING_FRONT
        }
        val targetId = findBestCameraId(targetFacing)
        if (targetId != currentCameraId) {
            closeCamera()
            currentCameraId = targetId
            if (surfaceTexture != null) {
                openCamera(surfaceTexture, currentSettings)
            }
        }
    }

    /**
     * Opens the camera and initializes the capture session.
     */
    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: SurfaceTexture, settings: NaturaSettings) {
        if (isOpeningCamera) {
            Log.d(TAG, "Camera open already in progress, ignoring duplicate call")
            return
        }
        if (cameraDevice != null && activeSurfaceTexture == surfaceTexture) {
            Log.d(TAG, "Camera already opened with active surface texture")
            return
        }

        startBackgroundThread()
        currentSettings = settings
        activeSurfaceTexture = surfaceTexture
        isOpeningCamera = true

        try {
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
            currentCapabilities = CameraCapabilities.from(characteristics, currentCameraId)

            val handler = backgroundHandler ?: run {
                isOpeningCamera = false
                return
            }
            cameraManager.openCamera(currentCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    isOpeningCamera = false
                    cameraDevice = camera
                    createCameraPreviewSession(surfaceTexture)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    isOpeningCamera = false
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    isOpeningCamera = false
                    camera.close()
                    cameraDevice = null
                    val msg = when (error) {
                        ERROR_CAMERA_IN_USE -> "Camera is currently in use by another application."
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum number of cameras already in use."
                        ERROR_CAMERA_DISABLED -> "Camera is disabled on this device by policy."
                        ERROR_CAMERA_DEVICE -> "A fatal camera device error occurred."
                        ERROR_CAMERA_SERVICE -> "The Android camera system service failed."
                        else -> "Camera error code: $error"
                    }
                    onError(msg)
                }
            }, handler)
        } catch (e: SecurityException) {
            isOpeningCamera = false
            onError("Camera permission is required to operate Natura.")
        } catch (e: Exception) {
            isOpeningCamera = false
            onError("Unable to access camera: ${e.localizedMessage}")
        }
    }

    /**
     * Configures surfaces and establishes CameraCaptureSession.
     */
    private fun createCameraPreviewSession(surfaceTexture: SurfaceTexture) {
        val device = cameraDevice ?: return
        val caps = currentCapabilities ?: return
        val handler = backgroundHandler ?: return

        if (isCreatingSession) {
            Log.d(TAG, "Session creation in progress, ignoring concurrent request")
            return
        }
        isCreatingSession = true

        try {
            // Stop and close any previous session before establishing a new one
            try {
                captureSession?.stopRepeating()
                captureSession?.abortCaptures()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping previous capture session", e)
            }
            captureSession?.close()
            captureSession = null

            // Configure JPEG ImageReader using supported sizes
            val jpegSize = when (currentSettings.resolution) {
                ResolutionOption.MAX -> caps.maxJpegSize
                ResolutionOption.MEDIUM -> caps.mediumJpegSize
            } ?: caps.jpegOutputSizes.firstOrNull() ?: Size(1280, 720)

            jpegImageReader?.close()
            jpegImageReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    handleJpegImage(image)
                }, handler)
            }

            // Configure RAW ImageReader if supported by hardware and requested
            rawImageReader?.close()
            rawImageReader = null
            val isRawRequested = (currentSettings.outputFormat == OutputFormat.JPEG_DNG || currentSettings.cameraMode == CameraMode.RAW)
            val isRawHardwareCapable = caps.isRawSupported && caps.hardwareLevel != "LEGACY" && caps.rawOutputSizes.isNotEmpty()
            if (isRawRequested && isRawHardwareCapable) {
                val rawSize = caps.maxRawSize ?: caps.rawOutputSizes.firstOrNull()
                if (rawSize != null) {
                    try {
                        rawImageReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                            setOnImageAvailableListener({ reader ->
                                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                                handleRawImage(image)
                            }, handler)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed creating RAW ImageReader, continuing with JPEG", e)
                        rawImageReader = null
                    }
                }
            }

            // Pick optimal preview size from valid choices
            val optimalPreviewSize = chooseOptimalPreviewSize(caps.previewOutputSizes, currentSettings.aspectRatio)
            surfaceTexture.setDefaultBufferSize(optimalPreviewSize.width, optimalPreviewSize.height)

            // Maintain previewSurface without releasing the underlying SurfaceTexture BufferQueue
            if (previewSurface == null || !previewSurface!!.isValid) {
                previewSurface?.release()
                previewSurface = Surface(surfaceTexture)
            }

            val pSurface = previewSurface
            if (pSurface == null || !pSurface.isValid) {
                isCreatingSession = false
                onError("Preview surface is not valid.")
                return
            }

            val outputSurfaces = mutableListOf<Surface>().apply {
                add(pSurface)
                jpegImageReader?.surface?.let { if (it.isValid) add(it) }
                rawImageReader?.surface?.let { if (it.isValid) add(it) }
            }

            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                applyNaturalProcessingKeys(this, caps, currentSettings, isStill = false)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigs = outputSurfaces.map { android.hardware.camera2.params.OutputConfiguration(it) }
                val executor = java.util.concurrent.Executor { command -> handler.post(command) }
                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    executor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            isCreatingSession = false
                            if (cameraDevice == null) {
                                session.close()
                                return
                            }
                            captureSession = session
                            startRepeatingPreview()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            isCreatingSession = false
                            Log.e(TAG, "Session configuration failed.")
                            if (rawImageReader != null) {
                                Log.w(TAG, "Retrying capture session without RAW stream...")
                                rawImageReader?.close()
                                rawImageReader = null
                                createCameraPreviewSession(surfaceTexture)
                            } else {
                                onError("Failed to configure camera preview session.")
                            }
                        }
                    }
                )
                device.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(outputSurfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        isCreatingSession = false
                        if (cameraDevice == null) {
                            session.close()
                            return
                        }
                        captureSession = session
                        startRepeatingPreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        isCreatingSession = false
                        Log.e(TAG, "Session configuration failed.")
                        if (rawImageReader != null) {
                            Log.w(TAG, "Retrying capture session without RAW stream...")
                            rawImageReader?.close()
                            rawImageReader = null
                            createCameraPreviewSession(surfaceTexture)
                        } else {
                            onError("Failed to configure camera preview session.")
                        }
                    }
                }, handler)
            }

        } catch (e: Exception) {
            isCreatingSession = false
            Log.e(TAG, "Failed creating camera preview session", e)
            onError("Failed to start preview: ${e.localizedMessage}")
        }
    }

    /**
     * Starts repeating preview with live readout telemetry callbacks.
     */
    private fun startRepeatingPreview() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return

        try {
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    extractReadout(result)
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start repeating preview", e)
        }
    }

    /**
     * Applies Natura's natural low-processing pipeline keys to a [CaptureRequest.Builder].
     * Every key contains a rationale comment explaining its effect.
     */
    private fun applyNaturalProcessingKeys(
        builder: CaptureRequest.Builder,
        caps: CameraCapabilities,
        settings: NaturaSettings,
        isStill: Boolean
    ) {
        // --- 1. NOISE REDUCTION ---
        // Request the least noise reduction supported by the ISP.
        // MINIMAL preserves fine texture and filmic grain over aggressive watercolor smearing.
        if (caps.isNoiseReductionModeSupported(settings.noiseReduction.camera2Mode)) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, settings.noiseReduction.camera2Mode)
        } else if (caps.isNoiseReductionModeSupported(CaptureRequest.NOISE_REDUCTION_MODE_FAST)) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
        }

        // --- 2. EDGE SHARPENING ---
        // Default to OFF or MINIMAL (FAST) to prevent unnatural halo artifacts and synthetic edges.
        if (caps.isEdgeModeSupported(settings.edgeSharpening.camera2Mode)) {
            builder.set(CaptureRequest.EDGE_MODE, settings.edgeSharpening.camera2Mode)
        } else if (caps.isEdgeModeSupported(CaptureRequest.EDGE_MODE_FAST)) {
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
        }

        // --- 3. TONE MAPPING ---
        // Standard sRGB or linear tone curves rather than aggressive HDR tone compressor.
        if (settings.toneMapping == ToneMappingSetting.NEUTRAL &&
            caps.isToneMapModeSupported(CaptureRequest.TONEMAP_MODE_PRESET_CURVE)
        ) {
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE)
            builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_SRGB)
        } else if (caps.isToneMapModeSupported(CaptureRequest.TONEMAP_MODE_FAST)) {
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST)
        }

        // --- 4. COLOR CORRECTION ---
        // Turn off artificial aberration shifts to preserve true lens optical characteristics.
        if (settings.colorCorrection == ColorCorrectionSetting.FAITHFUL &&
            caps.supportedColorAberrationModes.contains(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
        ) {
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
        } else if (caps.supportedColorAberrationModes.contains(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST)) {
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST)
        }

        // --- 5. BEAUTY & AI EFFECTS: STRICTLY OFF ---
        // Natura philosophy: "Capture Reality. Not Algorithms."
        // Turn off all artificial color filters, scene recognition engines, and face modification.
        builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
        builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
        builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_FAST)
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_FAST)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)
        }

        // --- 6. ZOOM RATIO ---
        applyZoom(builder, caps, currentZoomRatio)

        // --- 7. FLASH & EXPOSURE ---
        applyExposureAndManualControls(builder, caps, settings)

        // --- 8. METERING REGIONS ---
        activeAfRegion?.let { builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(it)) }
        activeAeRegion?.let { builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(it)) }

        // --- 9. INTENT ---
        if (isStill) {
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
            builder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())
        }
    }

    /**
     * Applies manual PRO parameters or continuous automatic exposure/focus.
     */
    private fun applyExposureAndManualControls(
        builder: CaptureRequest.Builder,
        caps: CameraCapabilities,
        settings: NaturaSettings
    ) {
        val isManualExposure = (manualIso != null || manualShutterNanos != null)

        if (isManualExposure && caps.isManualSensorSupported) {
            // Turn off auto-exposure for true manual shutter/ISO
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            manualIso?.let { iso ->
                val clampedIso = caps.isoRange?.clamp(iso) ?: iso
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)
            }
            manualShutterNanos?.let { exp ->
                val clampedExp = caps.exposureTimeRange?.clamp(exp) ?: exp
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedExp)
            }
        } else {
            // Auto exposure with flash mode
            val aeMode = when (settings.flashMode) {
                FlashMode.OFF -> CaptureRequest.CONTROL_AE_MODE_ON
                FlashMode.ON -> if (caps.isFlashAvailable) CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH else CaptureRequest.CONTROL_AE_MODE_ON
                FlashMode.TORCH -> CaptureRequest.CONTROL_AE_MODE_ON
            }
            builder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)

            if (settings.flashMode == FlashMode.TORCH && caps.isFlashAvailable) {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }

            // EV compensation
            val clampedEv = caps.exposureCompensationRange.clamp(manualEv)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedEv)
        }

        // White Balance
        if (manualKelvin != 5500 && (caps.isManualPostProcessingSupported || caps.isManualSensorSupported)) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            val gains = kelvinToColorGains(manualKelvin)
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }

        // Focus
        if (manualFocusDistance != null && !caps.isFixedFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance)
        } else if (!caps.isFixedFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // Lock
        if (isAeAfLocked) {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, true)
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
        }
    }

    /**
     * Applies optical zoom ratio or scaler crop digital zoom.
     */
    private fun applyZoom(builder: CaptureRequest.Builder, caps: CameraCapabilities, zoomRatio: Float) {
        val clampedZoom = ZoomLensHelper.clampZoomRatio(zoomRatio, caps.zoomRatioRange)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedZoom)
        } else {
            val cropRect = ZoomLensHelper.computeCropRegion(caps.sensorActiveArraySize, clampedZoom)
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
        }
    }

    /**
     * Sets live zoom ratio.
     */
    fun setZoomRatio(ratio: Float) {
        val caps = currentCapabilities ?: return
        currentZoomRatio = ZoomLensHelper.clampZoomRatio(ratio, caps.zoomRatioRange)
        updateRepeatingRequest()
    }

    /**
     * Updates repeating preview with current builder settings.
     */
    fun updateRepeatingRequest() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        val caps = currentCapabilities ?: return
        val handler = backgroundHandler ?: return

        try {
            applyNaturalProcessingKeys(builder, caps, currentSettings, isStill = false)
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    extractReadout(result)
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating repeating request", e)
        }
    }

    /**
     * Extracts telemetry (ISO, shutter, EV, color temp) from ISP capture result.
     */
    private fun extractReadout(result: TotalCaptureResult) {
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100
        val expTimeNanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 4_000_000L
        val evSteps = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: 0
        val caps = currentCapabilities
        val evStepVal = caps?.exposureCompensationStep?.toFloat() ?: (1f / 3f)
        val evValue = evSteps * evStepVal

        val shutterStr = formatShutterSpeed(expTimeNanos)
        val evStr = if (abs(evValue) < 0.05f) "EV 0.0" else String.format("EV %+.1f", evValue)

        val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
        val estKelvin = estimateKelvinFromGains(gains)

        onReadoutUpdated(
            CaptureReadout(
                iso = iso,
                shutterText = shutterStr,
                evText = evStr,
                kelvin = estKelvin
            )
        )
    }

    /**
     * Tap to focus & meter on a normalized coordinate (0f..1f).
     */
    fun focusOnPoint(normalizedX: Float, normalizedY: Float, isLongPressLock: Boolean = false) {
        val caps = currentCapabilities ?: return
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val handler = backgroundHandler ?: return

        val activeArray = caps.sensorActiveArraySize
        val regionSize = 300 // 300px metering square
        val centerX = (normalizedX * activeArray.width()).toInt().coerceIn(regionSize / 2, activeArray.width() - regionSize / 2)
        val centerY = (normalizedY * activeArray.height()).toInt().coerceIn(regionSize / 2, activeArray.height() - regionSize / 2)

        val meterRect = Rect(
            centerX - regionSize / 2,
            centerY - regionSize / 2,
            centerX + regionSize / 2,
            centerY + regionSize / 2
        )
        val meterRegion = MeteringRectangle(meterRect, MeteringRectangle.METERING_WEIGHT_MAX)

        activeAfRegion = meterRegion
        activeAeRegion = meterRegion

        if (isLongPressLock) {
            isAeAfLocked = true
        }

        try {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meterRegion))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meterRegion))
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

            session.capture(builder.build(), null, handler)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            updateRepeatingRequest()
        } catch (e: Exception) {
            Log.e(TAG, "Failed triggering tap to focus", e)
        }
    }

    /**
     * Unlocks AE/AF and resets to continuous auto.
     */
    fun resetAuto() {
        isAeAfLocked = false
        manualIso = null
        manualShutterNanos = null
        manualEv = 0
        manualKelvin = 5500
        manualFocusDistance = null
        activeAfRegion = null
        activeAeRegion = null

        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return
        val handler = backgroundHandler ?: return
        try {
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            session.capture(builder.build(), null, handler)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            updateRepeatingRequest()
        } catch (e: Exception) {
            Log.e(TAG, "Failed resetting auto focus", e)
        }
    }

    /**
     * Executes still photo capture (JPEG, or paired RAW+JPEG).
     */
    fun takePhoto(onHapticTick: () -> Unit) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val caps = currentCapabilities ?: return
        val jpegReader = jpegImageReader ?: return
        val handler = backgroundHandler ?: return

        activeBaseName = MediaStoreSaver.generateBaseName()
        onHapticTick()

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(jpegReader.surface)
                rawImageReader?.let { addTarget(it.surface) }

                applyNaturalProcessingKeys(this, caps, currentSettings, isStill = true)

                // Calculate orientation for saved JPEG
                val jpegOrientation = orientationManager.computeJpegOrientation(caps.sensorOrientation, caps.isFrontCamera)
                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
            }

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
                    pendingCaptureResults[timestamp] = result

                    // Check if companion RAW image already arrived
                    val pairedRaw = pendingDngImages.remove(timestamp)
                    if (pairedRaw != null) {
                        saveDngImage(pairedRaw, result, caps)
                    }
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Error executing still capture", e)
            onError("Failed to take photo: ${e.localizedMessage}")
        }
    }

    private fun handleJpegImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        val baseName = activeBaseName.ifEmpty { MediaStoreSaver.generateBaseName() }
        coroutineScope.launch(Dispatchers.IO) {
            val uri = MediaStoreSaver.saveJpeg(context, baseName, bytes)
            onCaptureComplete(uri, null)
        }
    }

    private fun handleRawImage(image: Image) {
        val timestamp = image.timestamp
        val pairedResult = pendingCaptureResults.remove(timestamp)
        val caps = currentCapabilities
        if (pairedResult != null && caps != null) {
            saveDngImage(image, pairedResult, caps)
        } else {
            pendingDngImages[timestamp] = image
        }
    }

    private fun saveDngImage(rawImage: Image, result: CaptureResult, caps: CameraCapabilities) {
        val baseName = activeBaseName.ifEmpty { MediaStoreSaver.generateBaseName() }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val chars = cameraManager.getCameraCharacteristics(caps.cameraId)
                val pair = MediaStoreSaver.createDngStream(context, baseName)
                if (pair != null) {
                    val (uri, stream) = pair
                    val orientation = orientationManager.computeJpegOrientation(caps.sensorOrientation, caps.isFrontCamera)
                    stream.use { out ->
                        DngWriter.writeDng(chars, result, rawImage, out, orientation)
                    }
                    MediaStoreSaver.finishDng(context, uri)
                    onCaptureComplete(null, uri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving DNG", e)
            } finally {
                rawImage.close()
            }
        }
    }

    /**
     * Starts video recording using MediaRecorder with natural profile.
     */
    fun startVideoRecording(surfaceTexture: SurfaceTexture) {
        if (isRecordingVideo) return
        val device = cameraDevice ?: return
        val caps = currentCapabilities ?: return
        val handler = backgroundHandler ?: return

        try {
            val baseName = "NATURA_VID_${System.currentTimeMillis()}"
            val pair = MediaStoreSaver.createVideoPendingFile(context, baseName)
            if (pair == null) {
                onError("Failed to create video file.")
                return
            }
            val (uri, pfd) = pair
            currentRecordingUri = uri
            recordingFileDescriptor = pfd

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(pfd.fileDescriptor)
                setVideoEncodingBitRate(12_000_000)
                setVideoFrameRate(30)
                setVideoSize(1920, 1080)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                val orient = orientationManager.computeJpegOrientation(caps.sensorOrientation, caps.isFrontCamera)
                setOrientationHint(orient)
                prepare()
            }

            val videoSize = caps.previewOutputSizes.filter { it.width <= 1920 && it.height <= 1080 }
                .maxByOrNull { it.width.toLong() * it.height.toLong() } ?: Size(1280, 720)

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(pfd.fileDescriptor)
                setVideoEncodingBitRate(10_000_000)
                setVideoFrameRate(30)
                setVideoSize(videoSize.width, videoSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                val orient = orientationManager.computeJpegOrientation(caps.sensorOrientation, caps.isFrontCamera)
                setOrientationHint(orient)
                prepare()
            }

            val recorderSurface = mediaRecorder?.surface ?: return
            val pSurface = previewSurface ?: return

            // Stop and close preview session before recording session
            try {
                captureSession?.stopRepeating()
                captureSession?.abortCaptures()
            } catch (_: Exception) {}
            captureSession?.close()
            captureSession = null

            val recordingBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(pSurface)
                addTarget(recorderSurface)
                applyNaturalProcessingKeys(this, caps, currentSettings, isStill = false)
            }

            device.createCaptureSession(listOf(pSurface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(recordingBuilder.build(), null, handler)
                    mediaRecorder?.start()
                    isRecordingVideo = true
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("Failed to start video recording session.")
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing MediaRecorder", e)
            onError("Video recording failed: ${e.localizedMessage}")
        }
    }

    /**
     * Stops video recording and commits video in MediaStore.
     */
    fun stopVideoRecording(surfaceTexture: SurfaceTexture) {
        if (!isRecordingVideo) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null

            recordingFileDescriptor?.close()
            recordingFileDescriptor = null

            currentRecordingUri?.let { uri ->
                MediaStoreSaver.finishVideo(context, uri)
                onCaptureComplete(uri, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media recorder", e)
        } finally {
            isRecordingVideo = false
            currentRecordingUri = null
            createCameraPreviewSession(surfaceTexture)
        }
    }

    fun applySettings(newSettings: NaturaSettings, surfaceTexture: SurfaceTexture?) {
        val formatChanged = (newSettings.outputFormat != currentSettings.outputFormat) ||
                (newSettings.cameraMode != currentSettings.cameraMode) ||
                (newSettings.resolution != currentSettings.resolution) ||
                (newSettings.aspectRatio != currentSettings.aspectRatio)
        currentSettings = newSettings
        if (formatChanged && surfaceTexture != null && cameraDevice != null && !isCreatingSession) {
            createCameraPreviewSession(surfaceTexture)
        } else if (cameraDevice != null && captureSession != null && !isCreatingSession) {
            updateRepeatingRequest()
        }
    }

    fun closeCamera() {
        try {
            isOpeningCamera = false
            isCreatingSession = false
            activeSurfaceTexture = null
            try {
                captureSession?.stopRepeating()
                captureSession?.abortCaptures()
            } catch (_: Exception) {}
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            previewSurface?.release()
            previewSurface = null
            jpegImageReader?.close()
            jpegImageReader = null
            rawImageReader?.close()
            rawImageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    fun release() {
        closeCamera()
        stopBackgroundThread()
    }

    private fun chooseOptimalPreviewSize(choices: List<Size>, aspectRatio: AspectRatio): Size {
        if (choices.isEmpty()) return Size(1280, 720)
        val targetRatio = aspectRatio.ratio
        val filtered = choices.filter { size ->
            val w = max(size.width, size.height)
            val h = min(size.width, size.height)
            val r = w.toFloat() / h.toFloat()
            abs(r - targetRatio) < 0.08f
        }
        return filtered.filter { max(it.width, it.height) <= 1920 }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?: filtered.firstOrNull()
            ?: choices.filter { max(it.width, it.height) <= 1920 }
                .maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?: choices.first()
    }

    private fun formatShutterSpeed(nanos: Long): String {
        val seconds = nanos.toDouble() / 1_000_000_000.0
        return if (seconds >= 1.0) {
            String.format("%.1fs", seconds)
        } else {
            val denom = (1.0 / seconds).roundToInt()
            "1/$denom"
        }
    }

    private fun kelvinToColorGains(kelvin: Int): RggbChannelVector {
        // Approximate Planckian locus conversion
        val rGain = (1.0f + (5500 - kelvin).toFloat() / 2500f).coerceIn(1.0f, 3.5f)
        val bGain = (1.0f + (kelvin - 5500).toFloat() / 2500f).coerceIn(1.0f, 3.5f)
        return RggbChannelVector(rGain, 1.0f, 1.0f, bGain)
    }

    private fun estimateKelvinFromGains(gains: RggbChannelVector?): Int {
        if (gains == null) return 5500
        val r = gains.red
        val b = gains.blue
        val kelvin = 5500 + ((b - r) * 1500f).toInt()
        return kelvin.coerceIn(2500, 9000)
    }
}
