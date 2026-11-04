package com.natura.camera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import com.natura.camera.ui.theme.NaturaBorder
import com.natura.camera.ui.theme.NaturaTextPrimary

@Composable
fun LiveHistogram(
    bins: FloatArray,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 76.dp, height = 34.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(0.8.dp, NaturaBorder, RoundedCornerShape(4.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (bins.isEmpty()) return@Canvas
            val path = Path()
            val w = size.width
            val h = size.height
            val step = w / (bins.size - 1).coerceAtLeast(1)

            path.moveTo(0f, h)
            for (i in bins.indices) {
                val x = i * step
                val normalizedHeight = (bins[i] * h).coerceIn(0f, h)
                val y = h - normalizedHeight
                if (i == 0) {
                    path.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.lineTo(w, h)
            path.close()

            // Fill #F2F0EB at 35% alpha
            drawPath(
                path = path,
                color = NaturaTextPrimary.copy(alpha = 0.35f),
                style = Fill
            )
        }
    }
}
