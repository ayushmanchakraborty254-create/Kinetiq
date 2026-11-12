package com.ayushman.kinetiq.domain.model

data class UserPreferences(
    val isMonitoringEnabled: Boolean = false,
    val isChopEnabled: Boolean = true,
    val isTwistEnabled: Boolean = true,
    val chopSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    val twistSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    val isHapticEnabled: Boolean = true,
    val isPocketProtectionEnabled: Boolean = true,
    val isScreenOffMonitoringEnabled: Boolean = false,
    val customChopPeakThreshold: Float? = null,
    val customTwistPeakThreshold: Float? = null
)

