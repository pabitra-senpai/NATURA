package com.natura.camera.camera

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest

/**
 * Camera operational modes supported by Natura.
 */
enum class CameraMode(val displayName: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    RAW("RAW"),
    PRO("PRO")
}

/**
 * Image output formats. JPEG+DNG is only enabled if the camera hardware supports RAW.
 */
enum class OutputFormat(val badgeLabel: String) {
    JPEG("JPG"),
    JPEG_DNG("RAW+JPG")
}

/**
 * Viewfinder and capture aspect ratios.
 */
enum class AspectRatio(val displayName: String, val ratio: Float) {
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_1_1("1:1", 1f)
}

/**
 * Capture resolution presets.
 */
enum class ResolutionOption(val displayName: String) {
    MAX("Max"),
    MEDIUM("Medium")
}

/**
 * Self-timer countdown options.
 */
enum class TimerSetting(val displayName: String, val seconds: Int) {
    OFF("Off", 0),
    SEC_3("3s", 3),
    SEC_10("10s", 10)
}

/**
 * Flash control modes.
 */
enum class FlashMode(val displayName: String) {
    OFF("Off"),
    ON("On"),
    TORCH("Torch")
}

/**
 * Natural noise reduction modes mapped to Camera2 API constants.
 */
enum class NoiseReductionSetting(val displayName: String, val camera2Mode: Int) {
    OFF("Off", CaptureRequest.NOISE_REDUCTION_MODE_OFF),
    MINIMAL("Minimal", CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL),
    AUTO("Auto", CaptureRequest.NOISE_REDUCTION_MODE_FAST)
}

/**
 * Natural edge enhancement modes mapped to Camera2 API constants.
 */
enum class EdgeSharpeningSetting(val displayName: String, val camera2Mode: Int) {
    OFF("Off", CaptureRequest.EDGE_MODE_OFF),
    MINIMAL("Minimal", CaptureRequest.EDGE_MODE_FAST),
    AUTO("Auto", CaptureRequest.EDGE_MODE_HIGH_QUALITY)
}

/**
 * Natural tone curve modes. Neutral avoids aggressive dynamic range tone curve compression.
 */
enum class ToneMappingSetting(val displayName: String) {
    NEUTRAL("Neutral"),
    AUTO("Auto")
}

/**
 * Natural color correction mode. Faithful turns off artificial aberration shifts.
 */
enum class ColorCorrectionSetting(val displayName: String) {
    FAITHFUL("Faithful"),
    AUTO("Auto")
}

/**
 * Adjustable manual parameters in PRO mode.
 */
enum class ProParameter(val label: String) {
    ISO("ISO"),
    SHUTTER("SHUTTER"),
    EV("EV"),
    WB("WB"),
    FOCUS("FOCUS")
}

/**
 * Live sensor telemetry readout shown on the viewfinder overlay.
 */
data class CaptureReadout(
    val iso: Int = 100,
    val shutterText: String = "1/250",
    val evText: String = "EV 0.0",
    val kelvin: Int = 5500
) {
    val formatted: String
        get() = "ISO $iso · $shutterText · $evText · ${kelvin}K"
}

/**
 * User settings model persisted across app launches.
 */
data class NaturaSettings(
    val selectedCameraId: String = "0",
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val resolution: ResolutionOption = ResolutionOption.MAX,
    val gridEnabled: Boolean = false,
    val timerSetting: TimerSetting = TimerSetting.OFF,
    val flashMode: FlashMode = FlashMode.OFF,
    val noiseReduction: NoiseReductionSetting = NoiseReductionSetting.MINIMAL,
    val edgeSharpening: EdgeSharpeningSetting = EdgeSharpeningSetting.OFF,
    val toneMapping: ToneMappingSetting = ToneMappingSetting.NEUTRAL,
    val colorCorrection: ColorCorrectionSetting = ColorCorrectionSetting.FAITHFUL
)
