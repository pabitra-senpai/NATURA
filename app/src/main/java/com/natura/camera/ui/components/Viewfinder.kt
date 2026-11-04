package com.natura.camera.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.natura.camera.camera.*
import com.natura.camera.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun Viewfinder(
    aspectRatio: AspectRatio,
    isProMode: Boolean,
    gridEnabled: Boolean,
    rollDegrees: Float,
    isLevel: Boolean,
    zoomRatio: Float,
    availableZoomPresets: List<ZoomPreset>,
    outputFormat: OutputFormat,
    readout: CaptureReadout,
    isShutterFlashing: Boolean,
    timerCountdown: Int?,
    focusTapPoint: Offset?,
    isFocusBoxVisible: Boolean,
    isAeAfLocked: Boolean,
    isFrontCamera: Boolean,
    histogramBins: FloatArray,
    onSurfaceTextureAvailable: (SurfaceTexture) -> Unit,
    onFocusTap: (normalizedX: Float, normalizedY: Float) -> Unit,
    onFocusLongPress: (normalizedX: Float, normalizedY: Float) -> Unit,
    onZoomRatioChanged: (Float) -> Unit,
    onHistogramBitmapAvailable: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var isTextureAvailable by remember { mutableStateOf(false) }

    // Periodic histogram computation when in PRO mode
    LaunchedEffect(isProMode, isTextureAvailable) {
        if (!isProMode) return@LaunchedEffect
        while (isActive) {
            val tv = textureViewRef
            if (tv != null && isTextureAvailable && tv.isAvailable) {
                try {
                    val bmp = tv.getBitmap(64, 48)
                    if (bmp != null) {
                        onHistogramBitmapAvailable(bmp)
                    }
                } catch (_: Exception) {}
            }
            delay(250) // ~4 times per second
        }
    }

    val viewfinderHeight = if (isProMode) 436.dp else 520.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(viewfinderHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(NaturaGradientDark, NaturaGradientMid, NaturaGradientEnd)
                )
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        val newRatio = zoomRatio * zoom
                        onZoomRatioChanged(newRatio)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val normX = (offset.x / size.width).coerceIn(0f, 1f)
                        val normY = (offset.y / size.height).coerceIn(0f, 1f)
                        onFocusTap(normX, normY)
                    },
                    onLongPress = { offset ->
                        val normX = (offset.x / size.width).coerceIn(0f, 1f)
                        val normY = (offset.y / size.height).coerceIn(0f, 1f)
                        onFocusLongPress(normX, normY)
                    }
                )
            }
            .testTag("camera_viewfinder")
    ) {
        // Camera2 TextureView
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            textureViewRef = this@apply
                            isTextureAvailable = true

                            // Front camera preview mirror only
                            if (isFrontCamera) {
                                val matrix = Matrix()
                                matrix.setScale(-1f, 1f, width / 2f, height / 2f)
                                setTransform(matrix)
                            } else {
                                setTransform(Matrix())
                            }

                            onSurfaceTextureAvailable(surface)
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            if (isFrontCamera) {
                                val matrix = Matrix()
                                matrix.setScale(-1f, 1f, width / 2f, height / 2f)
                                setTransform(matrix)
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            isTextureAvailable = false
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Rule of thirds grid
        if (gridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val gridColor = Color.White.copy(alpha = 0.20f)
                val strokeWidth = 1.dp.toPx()

                // Vertical lines
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                drawLine(gridColor, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), strokeWidth)

                // Horizontal lines
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                drawLine(gridColor, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), strokeWidth)
            }
        }

        // Horizon Level: two short 40dp x 2dp amber lines, 180dp apart
        val animatedRoll by animateFloatAsState(
            targetValue = if (isLevel) 0f else rollDegrees,
            label = "HorizonRoll"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(-animatedRoll),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.width(180.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left 40dp x 2dp amber line
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 2.dp)
                        .background(if (isLevel) NaturaAmber else NaturaAmber.copy(alpha = 0.7f))
                )
                // Right 40dp x 2dp amber line
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 2.dp)
                        .background(if (isLevel) NaturaAmber else NaturaAmber.copy(alpha = 0.7f))
                )
            }
        }

        // Top-left: Format badge & Live readout
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pill Badge "RAW+JPG" or "JPG"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .border(1.dp, NaturaAmber, RoundedCornerShape(100))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = outputFormat.badgeLabel,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.08.sp,
                    color = NaturaAmber
                )
            }

            // Live Readout: "ISO 100 · 1/250 · EV 0.0 · 5500K"
            Text(
                text = readout.formatted,
                style = TabularCameraReadoutStyle,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Top-right: Live Histogram (Pro mode)
        if (isProMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                LiveHistogram(bins = histogramBins)
            }
        }

        // Tap-to-focus Square (64dp, 1.5dp amber outline, 4dp radius, fades after 1.5s)
        if (isFocusBoxVisible && focusTapPoint != null) {
            val density = LocalDensity.current
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val boxWidthPx = with(density) { 64.dp.toPx() }
                val boxHeightPx = with(density) { 64.dp.toPx() }
                val targetXPx = focusTapPoint.x * constraints.maxWidth - boxWidthPx / 2
                val targetYPx = focusTapPoint.y * constraints.maxHeight - boxHeightPx / 2

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                targetXPx.toInt().coerceIn(0, constraints.maxWidth - boxWidthPx.toInt()),
                                targetYPx.toInt().coerceIn(0, constraints.maxHeight - boxHeightPx.toInt())
                            )
                        }
                        .size(64.dp)
                        .border(1.5.dp, NaturaAmber, RoundedCornerShape(4.dp))
                ) {
                    if (isAeAfLocked) {
                        Text(
                            text = "LOCK",
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            color = NaturaAmber,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }

        // Timer Countdown Number (Large centered overlay)
        if (timerCountdown != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$timerCountdown",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 80.sp,
                    color = NaturaAmber
                )
            }
        }

        // Bottom Center Zoom Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(100))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            availableZoomPresets.forEach { preset ->
                val isActive = (zoomRatio - preset.ratio) in -0.08f..0.08f
                val btnSize = if (isActive) 40.dp else 34.dp
                Box(
                    modifier = Modifier
                        .size(btnSize)
                        .clip(CircleShape)
                        .background(if (isActive) NaturaAmber else Color.Black.copy(alpha = 0.55f))
                        .clickable { onZoomRatioChanged(preset.ratio) }
                        .testTag("zoom_btn_${preset.label}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.label,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = if (isActive) NaturaBlack else NaturaTextPrimary
                    )
                }
            }
        }

        // Shutter feedback: 80ms black overlay flash
        AnimatedVisibility(
            visible = isShutterFlashing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}
