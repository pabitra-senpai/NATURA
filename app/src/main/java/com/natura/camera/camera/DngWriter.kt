package com.natura.camera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import android.util.Log
import java.io.OutputStream

/**
 * Handles creation of standard Adobe DNG (RAW) files using the Android Camera2 [DngCreator].
 * Embeds sensor metadata, color matrix, calibration illumination, and capture result directly from the ISP.
 */
object DngWriter {
    private const val TAG = "NaturaDngWriter"

    fun writeDng(
        characteristics: CameraCharacteristics,
        captureResult: CaptureResult,
        rawImage: Image,
        outputStream: OutputStream,
        orientation: Int
    ): Boolean {
        return try {
            DngCreator(characteristics, captureResult).use { creator ->
                creator.setOrientation(orientation)
                creator.writeImage(outputStream, rawImage)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write DNG raw image", e)
            false
        }
    }
}
