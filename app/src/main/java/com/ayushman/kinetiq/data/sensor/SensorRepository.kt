package com.ayushman.kinetiq.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.ayushman.kinetiq.domain.model.SensorReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorRepository(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    fun getHardwareStatus(): SensorHardwareStatus {
        val linAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        var hasFlash = false
        var flashId: String? = null
        try {
            cameraManager?.let { cm ->
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        hasFlash = true
                        flashId = id
                        break
                    }
                }
            }
        } catch (_: Exception) {}

        return SensorHardwareStatus(
            hasLinearAcceleration = linAccel != null,
            linearAccelerationName = linAccel?.name,
            linearAccelerationPowerMa = linAccel?.power ?: 0f,
            hasGyroscope = gyro != null,
            gyroscopeName = gyro?.name,
            gyroscopePowerMa = gyro?.power ?: 0f,
            hasProximity = prox != null,
            proximityName = prox?.name,
            hasFlashlight = hasFlash,
            cameraFlashId = flashId
        )
    }

    /**
     * Reactive stream of linear acceleration readings.
     * Prefers wake-up Sensor.TYPE_LINEAR_ACCELERATION to support screen-off gesture detection
     * on hardware that supports it, falling back to digital gravity separation
     * on Sensor.TYPE_ACCELEROMETER if unavailable.
     */
    fun linearAccelerationFlow(
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME
    ): Flow<SensorReading.LinearAcceleration> = callbackFlow {
        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rawAccelSensor = if (linearSensor == null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else null

        val targetSensor = linearSensor ?: rawAccelSensor
        if (targetSensor == null) {
            close()
            return@callbackFlow
        }

        val isFallback = (linearSensor == null)
        val gravity = FloatArray(3)
        var gravityInitialized = false
        val alpha = 0.8f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (isFallback) {
                    if (!gravityInitialized) {
                        gravity[0] = event.values[0]
                        gravity[1] = event.values[1]
                        gravity[2] = event.values[2]
                        gravityInitialized = true
                    } else {
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                    }

                    val linX = event.values[0] - gravity[0]
                    val linY = event.values[1] - gravity[1]
                    val linZ = event.values[2] - gravity[2]

                    trySend(SensorReading.LinearAcceleration(linX, linY, linZ, event.timestamp))
                } else {
                    trySend(
                        SensorReading.LinearAcceleration(
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2],
                            timestampNs = event.timestamp
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, targetSensor, samplingPeriodUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Reactive stream of gyroscope readings.
     * Prefers wake-up sensor variant to maximize background/screen-off compatibility where supported.
     */
    fun gyroscopeFlow(
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME
    ): Flow<SensorReading.Gyroscope> = callbackFlow {
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroSensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    SensorReading.Gyroscope(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestampNs = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, gyroSensor, samplingPeriodUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Reactive stream of raw accelerometer readings (including static gravity).
     * Used for physical sensor diagnostic recording and comparing finite difference models.
     */
    fun accelerometerFlow(
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME
    ): Flow<SensorReading.Accelerometer> = callbackFlow {
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelSensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    SensorReading.Accelerometer(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestampNs = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelSensor, samplingPeriodUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Reactive stream of proximity readings for accidental gesture suppression.
     */
    fun proximityFlow(): Flow<SensorReading.Proximity> = callbackFlow {
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor == null) {
            close()
            return@callbackFlow
        }

        val maxRange = proximitySensor.maximumRange

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values[0]
                // A reading less than maxRange (or < 4cm) generally denotes NEAR/covered
                val isNear = distance < maxRange && distance < 4.0f
                trySend(
                    SensorReading.Proximity(
                        distanceCm = distance,
                        maxRangeCm = maxRange,
                        isNear = isNear,
                        timestampNs = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
