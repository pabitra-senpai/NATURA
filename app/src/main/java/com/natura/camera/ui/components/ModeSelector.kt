package com.natura.camera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.natura.camera.camera.CameraMode
import com.natura.camera.ui.theme.ManropeFontFamily
import com.natura.camera.ui.theme.NaturaAmber
import com.natura.camera.ui.theme.NaturaBlack
import com.natura.camera.ui.theme.NaturaTextMuted
import com.natura.camera.ui.theme.NaturaTextPrimary

@Composable
fun ModeSelector(
    currentMode: CameraMode,
    isRawHardwareSupported: Boolean,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(NaturaBlack),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modes = CameraMode.values()
        modes.forEachIndexed { index, mode ->
            val isSelected = currentMode == mode
            val isEnabled = (mode != CameraMode.RAW) || isRawHardwareSupported

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = isEnabled
                    ) {
                        onModeSelected(mode)
                    }
                    .padding(horizontal = 15.dp, vertical = 6.dp)
                    .testTag("mode_${mode.name.lowercase()}")
            ) {
                Text(
                    text = mode.displayName,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.12.sp,
                    color = when {
                        !isEnabled -> NaturaTextMuted.copy(alpha = 0.35f)
                        isSelected -> NaturaTextPrimary
                        else -> NaturaTextMuted
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 4dp amber dot below active item, transparent dot below others
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) NaturaAmber else Color.Transparent)
                )
            }
        }
    }
}
