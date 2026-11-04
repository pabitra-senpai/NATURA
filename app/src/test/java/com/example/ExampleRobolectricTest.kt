package com.example

import com.natura.camera.camera.NoiseReductionSetting
import com.natura.camera.camera.EdgeSharpeningSetting
import com.natura.camera.camera.ToneMappingSetting
import com.natura.camera.camera.ColorCorrectionSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun naturaPhilosophy_defaultSettingsAreMinimalOrOff() {
        assertEquals("Minimal", NoiseReductionSetting.MINIMAL.displayName)
        assertEquals("Off", EdgeSharpeningSetting.OFF.displayName)
        assertEquals("Neutral", ToneMappingSetting.NEUTRAL.displayName)
        assertEquals("Faithful", ColorCorrectionSetting.FAITHFUL.displayName)
    }
}
