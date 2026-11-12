package com.ayushman.kinetiq.domain.model

import kotlin.math.sqrt

sealed class SensorReading {
    abstract val timestampNs: Long

    data class LinearAcceleration(
        val x: Float,
        val y: Float,
        val z: Float,
        override val timestampNs: Long
    ) : SensorReading() {
        val magnitude: Float = sqrt(x * x + y * y + z * z)
        // Primary chop axes are y (along device length) and z (perpendicular to screen)
        val longitudinalMagnitude: Float = sqrt(y * y + z * z)
    }

    data class Accelerometer(
        val x: Float,
        val y: Float,
        val z: Float,
        override val timestampNs: Long
    ) : SensorReading() {
        val magnitude: Float = sqrt(x * x + y * y + z * z)
    }

    data class Gyroscope(
        val x: Float,
        val y: Float,
        val z: Float,
        override val timestampNs: Long
    ) : SensorReading() {
        val angularSpeed: Float = sqrt(x * x + y * y + z * z)
        // Longitudinal axis for wrist twist (phone roll/twist along screen z or y axis)
        // Standard phone coordinate system:
        // x = lateral (left/right), y = longitudinal (top/bottom), z = normal (screen surface)
        // A wrist twist rotates the phone around its long axis (y-axis) or around z-axis when held vertically.
    }

    data class Proximity(
        val distanceCm: Float,
        val maxRangeCm: Float,
        val isNear: Boolean,
        override val timestampNs: Long
    ) : SensorReading()
}

