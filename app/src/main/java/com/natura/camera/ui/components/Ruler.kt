package com.natura.camera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natura.camera.ui.theme.*
import kotlin.math.roundToInt

data class RulerItem(
    val id: String,
    val displayLabel: String,
    val isAuto: Boolean = false
)

@Composable
fun RulerSlider(
    items: List<RulerItem>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tickSpacingPx = with(density) { 14.dp.toPx() }

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    val currentItem = items.getOrElse(selectedIndex) { items.firstOrNull() ?: RulerItem("", "") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Ruler Canvas with ticks and fixed center amber needle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        accumulatedDrag += delta
                        val steps = (accumulatedDrag / tickSpacingPx).toInt()
                        if (steps != 0) {
                            val newIdx = (selectedIndex - steps).coerceIn(0, items.size - 1)
                            if (newIdx != selectedIndex) {
                                onIndexSelected(newIdx)
                                accumulatedDrag -= steps * tickSpacingPx
                            }
                        }
                    }
                )
                .testTag("ruler_slider_canvas"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val baselineY = size.height - 4f

                // Draw ticks around selected index
                val visibleCount = (cx / tickSpacingPx).toInt() + 2
                for (offset in -visibleCount..visibleCount) {
                    val idx = selectedIndex + offset
                    if (idx in items.indices) {
                        val tickX = cx + (offset * tickSpacingPx) + accumulatedDrag
                        val isMajor = (idx % 5 == 0)
                        val tickHeight = if (isMajor) 24.dp.toPx() else 14.dp.toPx()
                        val tickColor = NaturaRulerTicks
                        drawLine(
                            color = tickColor,
                            start = Offset(tickX, baselineY - tickHeight),
                            end = Offset(tickX, baselineY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Fixed amber center indicator: 2dp wide, 44dp tall
                val needleHeight = 44.dp.toPx()
                drawLine(
                    color = NaturaAmber,
                    start = Offset(cx, baselineY - needleHeight),
                    end = Offset(cx, baselineY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Current Value Label beneath in Amber weight 800 tabular
        Text(
            text = currentItem.displayLabel,
            style = TabularRulerValueStyle.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = NaturaAmber
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
