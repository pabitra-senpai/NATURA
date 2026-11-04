package com.natura.camera.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natura.camera.camera.*
import com.natura.camera.ui.components.NaturaSegmentedControl
import com.natura.camera.ui.components.SegmentOption
import com.natura.camera.ui.theme.*

@Composable
fun SettingsScreen(
    settings: NaturaSettings,
    capabilities: CameraCapabilities?,
    onUpdateSettings: ((NaturaSettings) -> NaturaSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(NaturaBlack)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to camera",
                        tint = NaturaTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Natural Processing",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = NaturaTextPrimary
                )
            }
        },
        containerColor = NaturaBlack,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Subtitle
            Text(
                text = "Natura asks your camera for the least processing it supports. Options your device does not offer are dimmed.",
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = NaturaTextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // ================= IMAGE PIPELINE =================
            SectionHeader(title = "IMAGE PIPELINE")

            // 1. Noise reduction: Off | Minimal | Auto
            val nrOffSupported = capabilities?.isNoiseReductionModeSupported(android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_OFF) ?: false
            val nrMinSupported = capabilities?.isNoiseReductionModeSupported(android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL) ?: false
            val nrAutoSupported = capabilities?.isNoiseReductionModeSupported(android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_FAST) ?: true

            SettingRow(
                label = "Noise reduction",
                isSupported = nrOffSupported || nrMinSupported,
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(NoiseReductionSetting.OFF, "Off", nrOffSupported),
                            SegmentOption(NoiseReductionSetting.MINIMAL, "Minimal", nrMinSupported),
                            SegmentOption(NoiseReductionSetting.AUTO, "Auto", nrAutoSupported)
                        ),
                        selected = settings.noiseReduction,
                        onSelected = { mode ->
                            onUpdateSettings { it.copy(noiseReduction = mode) }
                        }
                    )
                }
            )

            // 2. Edge sharpening: Off | Minimal | Auto
            val edgeOffSupported = capabilities?.isEdgeModeSupported(android.hardware.camera2.CaptureRequest.EDGE_MODE_OFF) ?: false
            val edgeMinSupported = capabilities?.isEdgeModeSupported(android.hardware.camera2.CaptureRequest.EDGE_MODE_FAST) ?: false
            val edgeAutoSupported = capabilities?.isEdgeModeSupported(android.hardware.camera2.CaptureRequest.EDGE_MODE_HIGH_QUALITY) ?: true

            SettingRow(
                label = "Edge sharpening",
                isSupported = edgeOffSupported || edgeMinSupported,
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(EdgeSharpeningSetting.OFF, "Off", edgeOffSupported),
                            SegmentOption(EdgeSharpeningSetting.MINIMAL, "Minimal", edgeMinSupported),
                            SegmentOption(EdgeSharpeningSetting.AUTO, "Auto", edgeAutoSupported)
                        ),
                        selected = settings.edgeSharpening,
                        onSelected = { mode ->
                            onUpdateSettings { it.copy(edgeSharpening = mode) }
                        }
                    )
                }
            )

            // 3. Tone mapping: Neutral | Auto
            val toneNeutralSupported = capabilities?.isToneMapModeSupported(android.hardware.camera2.CaptureRequest.TONEMAP_MODE_PRESET_CURVE) ?: false
            SettingRow(
                label = "Tone mapping",
                isSupported = toneNeutralSupported,
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(ToneMappingSetting.NEUTRAL, "Neutral", toneNeutralSupported),
                            SegmentOption(ToneMappingSetting.AUTO, "Auto", true)
                        ),
                        selected = settings.toneMapping,
                        onSelected = { mode ->
                            onUpdateSettings { it.copy(toneMapping = mode) }
                        }
                    )
                }
            )

            // 4. Color correction: Faithful | Auto
            val colorFaithfulSupported = capabilities?.supportedColorAberrationModes?.contains(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF) ?: false
            SettingRow(
                label = "Color correction",
                isSupported = colorFaithfulSupported,
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(ColorCorrectionSetting.FAITHFUL, "Faithful", colorFaithfulSupported),
                            SegmentOption(ColorCorrectionSetting.AUTO, "Auto", true)
                        ),
                        selected = settings.colorCorrection,
                        onSelected = { mode ->
                            onUpdateSettings { it.copy(colorCorrection = mode) }
                        }
                    )
                }
            )

            // 5. Beauty & AI effects: Lock icon + "Always off" (non-interactive)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Beauty & AI effects",
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = NaturaTextPrimary
                    )
                    Text(
                        text = "Enforced in camera pipeline",
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = NaturaTextHint
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Enforced off",
                        tint = NaturaTextHint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Always off",
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NaturaTextMuted
                    )
                }
            }
            HorizontalDivider(color = NaturaDivider, thickness = 1.dp)

            // ================= OUTPUT =================
            SectionHeader(title = "OUTPUT")

            // Format: JPEG | JPEG+DNG
            val isRawSupported = capabilities?.isRawSupported ?: false
            SettingRow(
                label = "Format",
                isSupported = isRawSupported,
                unsupportedMessage = "RAW not supported on this device",
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(OutputFormat.JPEG, "JPEG", true),
                            SegmentOption(OutputFormat.JPEG_DNG, "JPEG+DNG", isRawSupported)
                        ),
                        selected = settings.outputFormat,
                        onSelected = { fmt ->
                            onUpdateSettings { it.copy(outputFormat = fmt) }
                        }
                    )
                }
            )

            // Aspect ratio: 4:3 | 16:9 | 1:1
            SettingRow(
                label = "Aspect ratio",
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(AspectRatio.RATIO_4_3, "4:3", true),
                            SegmentOption(AspectRatio.RATIO_16_9, "16:9", true),
                            SegmentOption(AspectRatio.RATIO_1_1, "1:1", true)
                        ),
                        selected = settings.aspectRatio,
                        onSelected = { r ->
                            onUpdateSettings { it.copy(aspectRatio = r) }
                        }
                    )
                }
            )

            // Resolution: Max | Medium
            SettingRow(
                label = "Resolution",
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(ResolutionOption.MAX, "Max", true),
                            SegmentOption(ResolutionOption.MEDIUM, "Medium", true)
                        ),
                        selected = settings.resolution,
                        onSelected = { res ->
                            onUpdateSettings { it.copy(resolution = res) }
                        }
                    )
                }
            )

            // JPEG Quality: 100 fixed (display only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JPEG quality",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = NaturaTextPrimary
                )
                Text(
                    text = "100 (Uncompressed)",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NaturaTextMuted
                )
            }
            HorizontalDivider(color = NaturaDivider, thickness = 1.dp)

            // Grid: Off | On
            SettingRow(
                label = "Rule-of-thirds grid",
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(false, "Off", true),
                            SegmentOption(true, "On", true)
                        ),
                        selected = settings.gridEnabled,
                        onSelected = { enabled ->
                            onUpdateSettings { it.copy(gridEnabled = enabled) }
                        }
                    )
                }
            )

            // Timer: Off | 3s | 10s
            SettingRow(
                label = "Self-timer",
                control = {
                    NaturaSegmentedControl(
                        options = listOf(
                            SegmentOption(TimerSetting.OFF, "Off", true),
                            SegmentOption(TimerSetting.SEC_3, "3s", true),
                            SegmentOption(TimerSetting.SEC_10, "10s", true)
                        ),
                        selected = settings.timerSetting,
                        onSelected = { tm ->
                            onUpdateSettings { it.copy(timerSetting = tm) }
                        }
                    )
                }
            )

            // ================= DEVICE CAPABILITIES =================
            SectionHeader(title = "DEVICE CAPABILITIES")

            if (capabilities != null) {
                CapabilityItem("Hardware level", capabilities.hardwareLevel)
                CapabilityItem("RAW capture capability", if (capabilities.isRawSupported) "Supported (RAW_SENSOR available)" else "Unsupported")
                CapabilityItem("Manual sensor control", if (capabilities.isManualSensorSupported) "Supported" else "Unsupported")
                CapabilityItem("Manual post-processing", if (capabilities.isManualPostProcessingSupported) "Supported" else "Unsupported")
                CapabilityItem("Flash unit", if (capabilities.isFlashAvailable) "Available" else "Not available")
                CapabilityItem("Focus system", if (capabilities.isFixedFocus) "Fixed focus" else "Autofocus supported")
                CapabilityItem("ISO range", capabilities.isoRange?.let { "${it.lower} - ${it.upper}" } ?: "Auto only")
                CapabilityItem("Exposure range", capabilities.exposureTimeRange?.let {
                    val minSec = it.lower / 1_000_000_000.0
                    val maxSec = it.upper / 1_000_000_000.0
                    String.format("%.4fs - %.1fs", minSec, maxSec)
                } ?: "Auto only")
                CapabilityItem("Noise reduction modes", capabilities.supportedNoiseReductionModes.joinToString { modeIdToName(it) })
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer note pinned to the bottom
            Text(
                text = "Some phones apply fixed processing in hardware. Natura gives you the most natural output available and never claims an unprocessed JPEG.",
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = NaturaTextHint,
                modifier = Modifier.padding(bottom = 36.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 0.16.sp,
        color = NaturaAmber,
        modifier = Modifier.padding(top = 26.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(
    label: String,
    isSupported: Boolean = true,
    unsupportedMessage: String = "Not supported on this device",
    control: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isSupported) NaturaTextPrimary else NaturaTextPrimary.copy(alpha = 0.4f)
                )
                if (!isSupported) {
                    Text(
                        text = unsupportedMessage,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = NaturaTextHint
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            control()
        }
        HorizontalDivider(color = NaturaDivider, thickness = 1.dp)
    }
}

@Composable
private fun CapabilityItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = NaturaTextMuted
        )
        Text(
            text = value,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = NaturaTextPrimary
        )
    }
}

private fun modeIdToName(id: Int): String = when (id) {
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_OFF -> "OFF"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_FAST -> "FAST"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY -> "HIGH_QUALITY"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL -> "MINIMAL"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG -> "ZSL"
    else -> "ID_$id"
}
