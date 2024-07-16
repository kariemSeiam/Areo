package com.pigo.areo.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

open class CompassManager(private val context: Context) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private var bearing: Float = 0f
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    fun startCompassUpdates(): Flow<Float> = callbackFlow {
        val sensorEventListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> gravity = event.values
                    Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values
                }

                if (gravity != null && geomagnetic != null) {
                    val rotationMatrix = FloatArray(9)
                    val inclinationMatrix = FloatArray(9)
                    if (SensorManager.getRotationMatrix(
                            rotationMatrix, inclinationMatrix, gravity, geomagnetic
                        )
                    ) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        bearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (bearing < 0) {
                            bearing += 360
                        }
                        trySend(bearing).isSuccess
                    }
                }
            }
        }

        sensorManager?.registerListener(
            sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI
        )
        sensorManager?.registerListener(
            sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI
        )

        awaitClose {
            sensorManager?.unregisterListener(sensorEventListener, accelerometer)
            sensorManager?.unregisterListener(sensorEventListener, magnetometer)
        }
    }

    fun stop() {
        coroutineScope.cancel()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001

        fun checkAndRequestPermissions(activity: Activity): Boolean {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            )
            val requiredPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            return if (requiredPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity, requiredPermissions.toTypedArray(), PERMISSION_REQUEST_CODE
                )
                false
            } else {
                true
            }
        }

        fun handlePermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    return true
                }
            }
            return false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Implementation is handled within startCompassUpdates method
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Implementation is handled within startCompassUpdates method
    }
}
