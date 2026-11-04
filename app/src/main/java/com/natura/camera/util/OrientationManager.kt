package com.natura.camera.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Manages device rotation angles for JPEG EXIF orientation and the horizon level overlay.
 */
class OrientationManager(
    private val context: Context,
    private val onRollChanged: (rollDegrees: Float, isLevel: Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var orientationListener: OrientationEventListener? = null
    var deviceOrientationDegrees: Int = 0
        private set

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var smoothedRoll = 0f

    fun start() {
        rotationSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Map continuous orientation to nearest 90-degree quadrant
                deviceOrientationDegrees = when (orientation) {
                    in 45 until 135 -> 90
                    in 135 until 225 -> 180
                    in 225 until 315 -> 270
                    else -> 0
                }
            }
        }
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        orientationListener?.disable()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var roll = 0f

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // orientationAngles[2] is roll in radians
            roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            roll = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
        }

        // Apply gentle low-pass filter
        smoothedRoll = smoothedRoll * 0.75f + roll * 0.25f

        val isLevel = abs(smoothedRoll) <= 1.0f
        onRollChanged(smoothedRoll, isLevel)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Calculates the required JPEG_ORIENTATION based on device rotation and camera sensor orientation.
     */
    fun computeJpegOrientation(sensorOrientation: Int, isFrontCamera: Boolean): Int {
        val rotation = when (deviceOrientationDegrees) {
            90 -> 90
            180 -> 180
            270 -> 270
            else -> 0
        }
        return if (isFrontCamera) {
            (sensorOrientation + rotation) % 360
        } else {
            (sensorOrientation - rotation + 360) % 360
        }
    }
}
