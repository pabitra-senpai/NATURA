package com.natura.camera.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import android.util.Rational
import android.util.Size

/**
 * Encapsulates hardware inspection of a camera device via [CameraCharacteristics].
 * Ensures Natura never requests an unsupported mode and transparently reports device reality.
 */
data class CameraCapabilities(
    val cameraId: String,
    val lensFacing: Int,
    val hardwareLevel: String,
    val isRawSupported: Boolean,
    val isManualSensorSupported: Boolean,
    val isManualPostProcessingSupported: Boolean,
    val isFlashAvailable: Boolean,
    val isoRange: Range<Int>?,
    val exposureTimeRange: Range<Long>?,
    val exposureCompensationRange: Range<Int>,
    val exposureCompensationStep: Rational,
    val minimumFocusDistance: Float,
    val isAutoFocusSupported: Boolean,
    val supportedNoiseReductionModes: Set<Int>,
    val supportedEdgeModes: Set<Int>,
    val supportedToneMapModes: Set<Int>,
    val supportedColorAberrationModes: Set<Int>,
    val zoomRatioRange: Range<Float>,
    val sensorActiveArraySize: Rect,
    val sensorOrientation: Int,
    val jpegOutputSizes: List<Size>,
    val rawOutputSizes: List<Size>,
    val previewOutputSizes: List<Size>
) {
    val isFixedFocus: Boolean
        get() = minimumFocusDistance <= 0f

    val isFrontCamera: Boolean
        get() = lensFacing == CameraCharacteristics.LENS_FACING_FRONT

    val maxJpegSize: Size?
        get() = jpegOutputSizes.maxByOrNull { it.width.toLong() * it.height.toLong() }

    val mediumJpegSize: Size?
        get() {
            if (jpegOutputSizes.isEmpty()) return null
            val sorted = jpegOutputSizes.sortedByDescending { it.width.toLong() * it.height.toLong() }
            return sorted.getOrNull(sorted.size / 2) ?: sorted.first()
        }

    val maxRawSize: Size?
        get() = rawOutputSizes.maxByOrNull { it.width.toLong() * it.height.toLong() }

    fun isNoiseReductionModeSupported(mode: Int): Boolean = supportedNoiseReductionModes.contains(mode)
    fun isEdgeModeSupported(mode: Int): Boolean = supportedEdgeModes.contains(mode)
    fun isToneMapModeSupported(mode: Int): Boolean = supportedToneMapModes.contains(mode)

    companion object {
        fun from(characteristics: CameraCharacteristics, cameraId: String): CameraCapabilities {
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                ?: CameraCharacteristics.LENS_FACING_BACK

            val levelInt = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            val hardwareLevel = when (levelInt) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN ($levelInt)"
            }

            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val isRawSupported = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            val isManualSensorSupported = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val isManualPostProcessingSupported = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)

            val isFlashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

            val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

            val exposureCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                ?: Range(0, 0)
            val exposureCompensationStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                ?: Rational(1, 1)

            val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            val isAutoFocusSupported = afModes.any {
                it != CameraMetadata.CONTROL_AF_MODE_OFF
            } && minFocusDistance > 0f

            val nrModes = characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                ?: intArrayOf()
            val edgeModes = characteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
                ?: intArrayOf()
            val toneModes = characteristics.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
                ?: intArrayOf()
            val aberrationModes = characteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
                ?: intArrayOf()

            val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1f, 1f)
            } else {
                val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                Range(1f, maxZoom)
            }

            val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: Rect(0, 0, 1920, 1080)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
            val rawSizes = if (isRawSupported) {
                map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            val previewSizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList() ?: emptyList()

            return CameraCapabilities(
                cameraId = cameraId,
                lensFacing = facing,
                hardwareLevel = hardwareLevel,
                isRawSupported = isRawSupported && rawSizes.isNotEmpty(),
                isManualSensorSupported = isManualSensorSupported,
                isManualPostProcessingSupported = isManualPostProcessingSupported,
                isFlashAvailable = isFlashAvailable,
                isoRange = isoRange,
                exposureTimeRange = exposureTimeRange,
                exposureCompensationRange = exposureCompensationRange,
                exposureCompensationStep = exposureCompensationStep,
                minimumFocusDistance = minFocusDistance,
                isAutoFocusSupported = isAutoFocusSupported,
                supportedNoiseReductionModes = nrModes.toSet(),
                supportedEdgeModes = edgeModes.toSet(),
                supportedToneMapModes = toneModes.toSet(),
                supportedColorAberrationModes = aberrationModes.toSet(),
                zoomRatioRange = zoomRange,
                sensorActiveArraySize = activeArray,
                sensorOrientation = sensorOrientation,
                jpegOutputSizes = jpegSizes,
                rawOutputSizes = rawSizes,
                previewOutputSizes = previewSizes
            )
        }
    }
}
