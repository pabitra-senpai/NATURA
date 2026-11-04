package com.natura.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.natura.camera.camera.FlashMode
import com.natura.camera.ui.theme.*

@Composable
fun TopBar(
    flashMode: FlashMode,
    isFlashSupported: Boolean,
    isRecording: Boolean,
    recordingSeconds: Int,
    onFlashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(NaturaBlack)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Flash button (left)
        IconButton(
            onClick = onFlashClick,
            enabled = isFlashSupported && !isRecording,
            modifier = Modifier
                .size(48.dp)
                .testTag("flash_button")
        ) {
            val icon = when (flashMode) {
                FlashMode.OFF -> Icons.Outlined.FlashOff
                FlashMode.ON -> Icons.Outlined.FlashOn
                FlashMode.TORCH -> Icons.Outlined.FlashAuto
            }
            val tint = if (!isFlashSupported) {
                NaturaTextHint.copy(alpha = 0.4f)
            } else if (flashMode != FlashMode.OFF) {
                NaturaAmber
            } else {
                NaturaTextPrimary
            }
            Icon(
                imageVector = icon,
                contentDescription = "Flash mode: ${flashMode.displayName}",
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        // Center Wordmark or Recording Timer
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                val mins = recordingSeconds / 60
                val secs = recordingSeconds % 60
                val timeStr = String.format("%02d:%02d", mins, secs)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NaturaRed)
                    )
                    Text(
                        text = timeStr,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NaturaTextPrimary
                    )
                }
            } else {
                Text(
                    text = "NATURA",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp,
                    color = NaturaTextPrimary
                )
            }
        }

        // Settings button (right)
        IconButton(
            onClick = onSettingsClick,
            enabled = !isRecording,
            modifier = Modifier
                .size(48.dp)
                .testTag("settings_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = "Natural Processing Settings",
                tint = NaturaTextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
