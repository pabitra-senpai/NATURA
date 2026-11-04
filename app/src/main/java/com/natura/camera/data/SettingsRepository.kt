package com.natura.camera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.natura.camera.camera.AspectRatio
import com.natura.camera.camera.CameraMode
import com.natura.camera.camera.ColorCorrectionSetting
import com.natura.camera.camera.EdgeSharpeningSetting
import com.natura.camera.camera.FlashMode
import com.natura.camera.camera.NaturaSettings
import com.natura.camera.camera.NoiseReductionSetting
import com.natura.camera.camera.OutputFormat
import com.natura.camera.camera.ResolutionOption
import com.natura.camera.camera.TimerSetting
import com.natura.camera.camera.ToneMappingSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "natura_preferences")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val KEY_CAMERA_ID = stringPreferencesKey("camera_id")
        val KEY_CAMERA_MODE = stringPreferencesKey("camera_mode")
        val KEY_OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val KEY_ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val KEY_RESOLUTION = stringPreferencesKey("resolution")
        val KEY_GRID_ENABLED = booleanPreferencesKey("grid_enabled")
        val KEY_TIMER = stringPreferencesKey("timer")
        val KEY_FLASH = stringPreferencesKey("flash")
        val KEY_NOISE_REDUCTION = stringPreferencesKey("noise_reduction")
        val KEY_EDGE_SHARPENING = stringPreferencesKey("edge_sharpening")
        val KEY_TONE_MAPPING = stringPreferencesKey("tone_mapping")
        val KEY_COLOR_CORRECTION = stringPreferencesKey("color_correction")
    }

    val settingsFlow: Flow<NaturaSettings> = context.dataStore.data.map { prefs ->
        NaturaSettings(
            selectedCameraId = prefs[PreferencesKeys.KEY_CAMERA_ID] ?: "0",
            cameraMode = parseEnum(prefs[PreferencesKeys.KEY_CAMERA_MODE], CameraMode.PHOTO),
            outputFormat = parseEnum(prefs[PreferencesKeys.KEY_OUTPUT_FORMAT], OutputFormat.JPEG),
            aspectRatio = parseEnum(prefs[PreferencesKeys.KEY_ASPECT_RATIO], AspectRatio.RATIO_4_3),
            resolution = parseEnum(prefs[PreferencesKeys.KEY_RESOLUTION], ResolutionOption.MAX),
            gridEnabled = prefs[PreferencesKeys.KEY_GRID_ENABLED] ?: false,
            timerSetting = parseEnum(prefs[PreferencesKeys.KEY_TIMER], TimerSetting.OFF),
            flashMode = parseEnum(prefs[PreferencesKeys.KEY_FLASH], FlashMode.OFF),
            noiseReduction = parseEnum(prefs[PreferencesKeys.KEY_NOISE_REDUCTION], NoiseReductionSetting.MINIMAL),
            edgeSharpening = parseEnum(prefs[PreferencesKeys.KEY_EDGE_SHARPENING], EdgeSharpeningSetting.OFF),
            toneMapping = parseEnum(prefs[PreferencesKeys.KEY_TONE_MAPPING], ToneMappingSetting.NEUTRAL),
            colorCorrection = parseEnum(prefs[PreferencesKeys.KEY_COLOR_CORRECTION], ColorCorrectionSetting.FAITHFUL)
        )
    }

    suspend fun updateSettings(transform: (NaturaSettings) -> NaturaSettings) {
        context.dataStore.edit { prefs ->
            val current = NaturaSettings(
                selectedCameraId = prefs[PreferencesKeys.KEY_CAMERA_ID] ?: "0",
                cameraMode = parseEnum(prefs[PreferencesKeys.KEY_CAMERA_MODE], CameraMode.PHOTO),
                outputFormat = parseEnum(prefs[PreferencesKeys.KEY_OUTPUT_FORMAT], OutputFormat.JPEG),
                aspectRatio = parseEnum(prefs[PreferencesKeys.KEY_ASPECT_RATIO], AspectRatio.RATIO_4_3),
                resolution = parseEnum(prefs[PreferencesKeys.KEY_RESOLUTION], ResolutionOption.MAX),
                gridEnabled = prefs[PreferencesKeys.KEY_GRID_ENABLED] ?: false,
                timerSetting = parseEnum(prefs[PreferencesKeys.KEY_TIMER], TimerSetting.OFF),
                flashMode = parseEnum(prefs[PreferencesKeys.KEY_FLASH], FlashMode.OFF),
                noiseReduction = parseEnum(prefs[PreferencesKeys.KEY_NOISE_REDUCTION], NoiseReductionSetting.MINIMAL),
                edgeSharpening = parseEnum(prefs[PreferencesKeys.KEY_EDGE_SHARPENING], EdgeSharpeningSetting.OFF),
                toneMapping = parseEnum(prefs[PreferencesKeys.KEY_TONE_MAPPING], ToneMappingSetting.NEUTRAL),
                colorCorrection = parseEnum(prefs[PreferencesKeys.KEY_COLOR_CORRECTION], ColorCorrectionSetting.FAITHFUL)
            )
            val updated = transform(current)
            prefs[PreferencesKeys.KEY_CAMERA_ID] = updated.selectedCameraId
            prefs[PreferencesKeys.KEY_CAMERA_MODE] = updated.cameraMode.name
            prefs[PreferencesKeys.KEY_OUTPUT_FORMAT] = updated.outputFormat.name
            prefs[PreferencesKeys.KEY_ASPECT_RATIO] = updated.aspectRatio.name
            prefs[PreferencesKeys.KEY_RESOLUTION] = updated.resolution.name
            prefs[PreferencesKeys.KEY_GRID_ENABLED] = updated.gridEnabled
            prefs[PreferencesKeys.KEY_TIMER] = updated.timerSetting.name
            prefs[PreferencesKeys.KEY_FLASH] = updated.flashMode.name
            prefs[PreferencesKeys.KEY_NOISE_REDUCTION] = updated.noiseReduction.name
            prefs[PreferencesKeys.KEY_EDGE_SHARPENING] = updated.edgeSharpening.name
            prefs[PreferencesKeys.KEY_TONE_MAPPING] = updated.toneMapping.name
            prefs[PreferencesKeys.KEY_COLOR_CORRECTION] = updated.colorCorrection.name
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?, default: T): T {
        if (value == null) return default
        return try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
}
