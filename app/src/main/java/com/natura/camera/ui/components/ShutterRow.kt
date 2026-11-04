package com.natura.camera.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.natura.camera.camera.CameraMode
import com.natura.camera.camera.NaturaMediaItem
import com.natura.camera.ui.theme.*

@Composable
fun ShutterRow(
    cameraMode: CameraMode,
    isRecording: Boolean,
    latestMedia: NaturaMediaItem?,
    isProMode: Boolean,
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFlipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Gallery Thumbnail (52dp, 12dp radius, 1.5dp border #4A4A45)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, Color(0xFF4A4A45), RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(NaturaGradientDark, NaturaGradientMid)
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onGalleryClick
                )
                .testTag("gallery_thumbnail_button"),
            contentAlignment = Alignment.Center
        ) {
            if (latestMedia != null) {
                AsyncImage(
                    model = latestMedia.uri,
                    contentDescription = "Latest photo in gallery",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Center: Shutter Button (76dp or 68dp in Pro)
        val outerSize = if (isProMode) 68.dp else 76.dp
        val innerSize = if (isProMode) 56.dp else 62.dp

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = tween(durationMillis = 150),
            label = "ShutterScale"
        )

        val innerCornerRadius by animateDpAsState(
            targetValue = if (isRecording) 8.dp else (innerSize / 2),
            animationSpec = tween(durationMillis = 200),
            label = "ShutterShape"
        )
        val innerTargetSize by animateDpAsState(
            targetValue = if (isRecording) (innerSize * 0.55f) else innerSize,
            animationSpec = tween(durationMillis = 200),
            label = "ShutterInnerSize"
        )

        Box(
            modifier = Modifier
                .size(outerSize)
                .scale(scale)
                .clip(CircleShape)
                .border(4.dp, NaturaTextPrimary, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onShutterClick
                )
                .testTag("shutter_button"),
            contentAlignment = Alignment.Center
        ) {
            // Inner shutter element
            Box(
                modifier = Modifier
                    .size(innerTargetSize)
                    .clip(RoundedCornerShape(innerCornerRadius))
                    .background(if (isRecording) NaturaRed else NaturaTextPrimary)
            )
        }

        // Right: Camera Flip Button (52dp circle, #161616 background)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(NaturaSurfaceMedium)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFlipClick
                )
                .testTag("camera_flip_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FlipCameraAndroid,
                contentDescription = "Flip camera",
                tint = NaturaTextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
