package com.natura.camera.camera

import android.graphics.Rect
import android.util.Range
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manages optical and digital zoom levels based on device capabilities.
 */
data class ZoomPreset(
    val ratio: Float,
    val label: String
)

object ZoomLensHelper {
    private val CANDIDATE_PRESETS = listOf(
        ZoomPreset(0.5f, ".5"),
        ZoomPreset(1.0f, "1×"),
        ZoomPreset(2.0f, "2×"),
        ZoomPreset(5.0f, "5×")
    )

    /**
     * Determines available hardware zoom buttons based on device zoom ratio range.
     */
    fun getAvailablePresets(range: Range<Float>): List<ZoomPreset> {
        val lower = range.lower
        val upper = range.upper
        val available = CANDIDATE_PRESETS.filter { preset ->
            // Allow small epsilon tolerance
            preset.ratio >= (lower - 0.05f) && preset.ratio <= (upper + 0.05f)
        }
        return if (available.isEmpty() || !available.any { it.ratio == 1.0f }) {
            listOf(ZoomPreset(1.0f, "1×"))
        } else {
            available
        }
    }

    /**
     * Clamps a zoom ratio within the camera's supported range.
     */
    fun clampZoomRatio(requested: Float, range: Range<Float>): Float {
        return min(max(requested, range.lower), range.upper)
    }

    /**
     * Computes the digital crop rectangle for SCALER_CROP_REGION fallback on older APIs.
     */
    fun computeCropRegion(activeArray: Rect, zoomRatio: Float): Rect {
        val clampedZoom = max(1.0f, zoomRatio)
        val cropWidth = (activeArray.width() / clampedZoom).roundToInt()
        val cropHeight = (activeArray.height() / clampedZoom).roundToInt()
        val cropLeft = (activeArray.width() - cropWidth) / 2
        val cropTop = (activeArray.height() - cropHeight) / 2
        return Rect(
            activeArray.left + cropLeft,
            activeArray.top + cropTop,
            activeArray.left + cropLeft + cropWidth,
            activeArray.top + cropTop + cropHeight
        )
    }
}
