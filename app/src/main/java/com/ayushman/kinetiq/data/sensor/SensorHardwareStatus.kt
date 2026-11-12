package com.ayushman.kinetiq.data.sensor

data class SensorHardwareStatus(
    val hasLinearAcceleration: Boolean,
    val linearAccelerationName: String?,
    val linearAccelerationPowerMa: Float,
    val hasGyroscope: Boolean,
    val gyroscopeName: String?,
    val gyroscopePowerMa: Float,
    val hasProximity: Boolean,
    val proximityName: String?,
    val hasFlashlight: Boolean,
    val cameraFlashId: String?
)

