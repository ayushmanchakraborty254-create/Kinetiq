package com.ayushman.kinetiq.domain.model

enum class SensitivityLevel(val displayName: String, val description: String) {
    LOW(
        displayName = "Low",
        description = "Requires deliberate, forceful motion. Lowest false-positive rate."
    ),
    MEDIUM(
        displayName = "Medium",
        description = "Balanced for everyday handling and natural gesture speed."
    ),
    HIGH(
        displayName = "High",
        description = "Detects lighter movements. Best for subtle or quick gestures."
    );

    // Double Chop linear acceleration thresholds (m/s^2)
    val chopPeakThreshold: Float
        get() = when (this) {
            LOW -> 18.0f
            MEDIUM -> 13.5f
            HIGH -> 10.0f
        }

    val chopNeutralThreshold: Float
        get() = when (this) {
            LOW -> 4.5f
            MEDIUM -> 3.8f
            HIGH -> 3.2f
        }

    // Double Twist gyroscope angular velocity thresholds (rad/s)
    val twistAngularVelocityThreshold: Float
        get() = when (this) {
            LOW -> 5.5f
            MEDIUM -> 4.2f
            HIGH -> 3.2f
        }

    // Double Twist minimum integrated angular displacement (radians)
    val twistMinDisplacementRad: Float
        get() = when (this) {
            LOW -> Math.toRadians(38.0).toFloat()
            MEDIUM -> Math.toRadians(28.0).toFloat()
            HIGH -> Math.toRadians(20.0).toFloat()
        }
}

