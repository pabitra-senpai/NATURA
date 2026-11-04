package com.natura.camera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natura.camera.camera.CameraCapabilities
import com.natura.camera.camera.OutputFormat
import com.natura.camera.camera.ProParameter
import com.natura.camera.ui.theme.*

@Composable
fun ProPanel(
    selectedParam: ProParameter,
    manualIso: Int?,
    manualShutterNanos: Long?,
    manualEv: Int,
    manualKelvin: Int,
    manualFocusDistance: Float?,
    isAeAfLocked: Boolean,
    outputFormat: OutputFormat,
    capabilities: CameraCapabilities?,
    onParamSelected: (ProParameter) -> Unit,
    onIsoChanged: (Int?) -> Unit,
    onShutterChanged: (Long?) -> Unit,
    onEvChanged: (Int) -> Unit,
    onKelvinChanged: (Int) -> Unit,
    onFocusChanged: (Float?) -> Unit,
    onAutoReset: () -> Unit,
    onAeAfLockToggle: () -> Unit,
    onRawToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NaturaBlack)
    ) {
        // Parameter Row: 5 equal columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ProParameter.values().forEach { param ->
                val isSelected = (selectedParam == param)
                val valueStr = when (param) {
                    ProParameter.ISO -> if (manualIso == null) "AUTO" else "$manualIso"
                    ProParameter.SHUTTER -> if (manualShutterNanos == null) "AUTO" else formatShutterVal(manualShutterNanos)
                    ProParameter.EV -> if (manualEv == 0) "0.0" else String.format("%+.1f", manualEv / 3f)
                    ProParameter.WB -> if (manualKelvin == 5500) "AUTO" else "${manualKelvin}K"
                    ProParameter.FOCUS -> if (manualFocusDistance == null) "AUTO" else if (manualFocusDistance <= 0.05f) "∞" else String.format("%.1fm", 1f / manualFocusDistance)
                }

                val isParamSupported = when (param) {
                    ProParameter.ISO, ProParameter.SHUTTER -> capabilities?.isManualSensorSupported != false
                    ProParameter.EV -> true
                    ProParameter.WB -> capabilities?.isManualPostProcessingSupported != false || capabilities?.isManualSensorSupported != false
                    ProParameter.FOCUS -> capabilities?.isFixedFocus != true
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = isParamSupported
                        ) {
                            onParamSelected(param)
                        }
                        .padding(vertical = 6.dp)
                        .testTag("pro_col_${param.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 11sp weight-600 uppercase muted label
                    Text(
                        text = param.label,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.1.sp,
                        color = if (isSelected) NaturaAmber else NaturaTextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // 17sp weight-800 value
                    Text(
                        text = valueStr,
                        style = TabularProValueStyle,
                        color = if (isSelected) NaturaAmber else NaturaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // 2dp amber bottom border when selected
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(2.dp)
                            .background(if (isSelected) NaturaAmber else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Ruler Slider for active parameter
        when (selectedParam) {
            ProParameter.ISO -> {
                val isoSteps = listOf(null, 50, 100, 200, 400, 800, 1600, 3200, 6400)
                val items = isoSteps.map { iso ->
                    RulerItem(
                        id = iso?.toString() ?: "auto",
                        displayLabel = if (iso == null) "AUTO" else "ISO $iso",
                        isAuto = iso == null
                    )
                }
                val curIdx = isoSteps.indexOf(manualIso).coerceAtLeast(0)
                RulerSlider(
                    items = items,
                    selectedIndex = curIdx,
                    onIndexSelected = { idx -> onIsoChanged(isoSteps[idx]) }
                )
            }
            ProParameter.SHUTTER -> {
                val shutterNanos = listOf(
                    null,
                    250_000L,       // 1/4000
                    500_000L,       // 1/2000
                    1_000_000L,     // 1/1000
                    2_000_000L,     // 1/500
                    4_000_000L,     // 1/250
                    8_000_000L,     // 1/125
                    16_666_666L,    // 1/60
                    33_333_333L,    // 1/30
                    66_666_666L,    // 1/15
                    125_000_000L,   // 1/8
                    250_000_000L,   // 1/4
                    500_000_000L,   // 1/2
                    1_000_000_000L, // 1s
                    2_000_000_000L  // 2s
                )
                val items = shutterNanos.map { s ->
                    RulerItem(
                        id = s?.toString() ?: "auto",
                        displayLabel = if (s == null) "AUTO" else formatShutterVal(s),
                        isAuto = s == null
                    )
                }
                val curIdx = shutterNanos.indexOf(manualShutterNanos).coerceAtLeast(0)
                RulerSlider(
                    items = items,
                    selectedIndex = curIdx,
                    onIndexSelected = { idx -> onShutterChanged(shutterNanos[idx]) }
                )
            }
            ProParameter.EV -> {
                val evSteps = (-6..6).toList() // -2.0 EV to +2.0 EV in 1/3 steps
                val items = evSteps.map { ev ->
                    val evVal = ev / 3f
                    RulerItem(
                        id = ev.toString(),
                        displayLabel = if (ev == 0) "EV 0.0" else String.format("EV %+.1f", evVal)
                    )
                }
                val curIdx = evSteps.indexOf(manualEv).coerceAtLeast(6)
                RulerSlider(
                    items = items,
                    selectedIndex = curIdx,
                    onIndexSelected = { idx -> onEvChanged(evSteps[idx]) }
                )
            }
            ProParameter.WB -> {
                val kelvinSteps = listOf(5500, 2500, 3200, 4000, 4800, 5500, 6500, 7500, 9000)
                val items = kelvinSteps.map { k ->
                    RulerItem(
                        id = k.toString(),
                        displayLabel = if (k == 5500) "AUTO (5500K)" else "${k}K",
                        isAuto = k == 5500
                    )
                }
                val curIdx = kelvinSteps.indexOf(manualKelvin).coerceAtLeast(0)
                RulerSlider(
                    items = items,
                    selectedIndex = curIdx,
                    onIndexSelected = { idx -> onKelvinChanged(kelvinSteps[idx]) }
                )
            }
            ProParameter.FOCUS -> {
                val focusSteps = listOf(null, 0.0f, 0.5f, 1.0f, 2.0f, 3.33f, 5.0f, 10.0f)
                val items = focusSteps.map { f ->
                    RulerItem(
                        id = f?.toString() ?: "auto",
                        displayLabel = when {
                            f == null -> "AUTO"
                            f <= 0.01f -> "∞"
                            else -> String.format("%.1fm", 1f / f)
                        },
                        isAuto = f == null
                    )
                }
                val curIdx = focusSteps.indexOf(manualFocusDistance).coerceAtLeast(0)
                RulerSlider(
                    items = items,
                    selectedIndex = curIdx,
                    onIndexSelected = { idx -> onFocusChanged(focusSteps[idx]) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Chip Row (36dp pills, fully rounded, 12sp weight 800, 1dp border #3A3A36)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // AUTO chip
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(100))
                    .border(1.dp, NaturaBorder, RoundedCornerShape(100))
                    .clickable { onAutoReset() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AUTO",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = NaturaTextMuted
                )
            }

            // AE/AF LOCK chip
            val isLocked = isAeAfLocked
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(100))
                    .border(1.dp, if (isLocked) NaturaAmber else NaturaBorder, RoundedCornerShape(100))
                    .clickable { onAeAfLockToggle() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AE/AF LOCK",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = if (isLocked) NaturaAmber else NaturaTextMuted
                )
            }

            // RAW+JPG chip
            val isRawPlusJpg = (outputFormat == OutputFormat.JPEG_DNG)
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(100))
                    .border(1.dp, if (isRawPlusJpg) NaturaAmber else NaturaBorder, RoundedCornerShape(100))
                    .clickable { onRawToggle() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RAW+JPG",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = if (isRawPlusJpg) NaturaAmber else NaturaTextMuted
                )
            }
        }
    }
}

private fun formatShutterVal(nanos: Long): String {
    val seconds = nanos.toDouble() / 1_000_000_000.0
    return if (seconds >= 1.0) {
        String.format("%.1fs", seconds)
    } else {
        val denom = (1.0 / seconds).toInt()
        "1/$denom"
    }
}
