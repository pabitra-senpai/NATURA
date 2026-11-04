package com.example

import com.natura.camera.camera.AspectRatio
import com.natura.camera.camera.CameraMode
import com.natura.camera.camera.CaptureReadout
import com.natura.camera.camera.FlashMode
import com.natura.camera.camera.NaturaSettings
import com.natura.camera.camera.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun captureReadout_formatsCorrectly() {
        val readout = CaptureReadout(
            iso = 200,
            shutterText = "1/500",
            evText = "EV +0.3",
            kelvin = 5600
        )
        assertEquals("ISO 200 · 1/500 · EV +0.3 · 5600K", readout.formatted)
    }

    @Test
    fun defaultSettings_haveNaturalValues() {
        val settings = NaturaSettings()
        assertEquals(CameraMode.PHOTO, settings.cameraMode)
        assertEquals(AspectRatio.RATIO_4_3, settings.aspectRatio)
        assertEquals(OutputFormat.JPEG, settings.outputFormat)
        assertEquals(FlashMode.OFF, settings.flashMode)
    }
}
