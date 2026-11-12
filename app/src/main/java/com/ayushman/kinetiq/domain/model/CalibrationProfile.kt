package com.ayushman.kinetiq.domain.model

data class CalibrationProfile(
    val gestureType: GestureType,
    val recordedPeaks: List<Float>,
    val averagePeak: Float,
    val recommendedPeakThreshold: Float,
    val recommendedNeutralThreshold: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

