package com.natura.camera.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.natura.camera.ui.theme.*

data class SegmentOption<T>(
    val value: T,
    val label: String,
    val isSupported: Boolean = true
)

@Composable
fun <T> NaturaSegmentedControl(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NaturaSurfaceDark)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { opt ->
            val isCurrent = (opt.value == selected)
            val isEnabled = opt.isSupported

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isCurrent && isEnabled -> NaturaTextPrimary
                            isCurrent && !isEnabled -> NaturaTextMuted.copy(alpha = 0.3f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = isEnabled
                    ) {
                        onSelected(opt.value)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = opt.label,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = when {
                        !isEnabled -> NaturaTextMuted.copy(alpha = 0.35f)
                        isCurrent -> NaturaBlack
                        else -> NaturaTextMuted
                    }
                )
            }
        }
    }
}
