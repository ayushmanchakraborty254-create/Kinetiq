package com.ayushman.kinetiq.domain.calibration

import com.ayushman.kinetiq.domain.model.CalibrationProfile
import com.ayushman.kinetiq.domain.model.GestureType
import kotlin.math.max
import kotlin.math.min

class GestureCalibrationManager {

    private val samples = mutableListOf<Float>()

    fun startCalibration() {
        samples.clear()
    }

    fun recordPeak(peakValue: Float) {
        if (peakValue > 0f) {
            samples.add(peakValue)
        }
    }

    fun getSampleCount(): Int = samples.size

    fun calculateProfile(type: GestureType): CalibrationProfile? {
        if (samples.isEmpty()) return null

        val avg = samples.average().toFloat()

        return when (type) {
            GestureType.DOUBLE_CHOP -> {
                // Peak threshold set to ~75% of average deliberate peak
                val recommendedPeak = (avg * 0.75f).coerceIn(9.0f, 22.0f)
                val recommendedNeutral = (recommendedPeak * 0.30f).coerceIn(2.5f, 5.0f)
                CalibrationProfile(
                    gestureType = type,
                    recordedPeaks = samples.toList(),
                    averagePeak = avg,
                    recommendedPeakThreshold = recommendedPeak,
                    recommendedNeutralThreshold = recommendedNeutral
                )
            }

            GestureType.DOUBLE_TWIST -> {
                val recommendedVelocity = (avg * 0.75f).coerceIn(2.8f, 7.0f)
                val recommendedDisplacement = Math.toRadians(26.0).toFloat()
                CalibrationProfile(
                    gestureType = type,
                    recordedPeaks = samples.toList(),
                    averagePeak = avg,
                    recommendedPeakThreshold = recommendedVelocity,
                    recommendedNeutralThreshold = recommendedDisplacement
                )
            }
        }
    }

    fun reset() {
        samples.clear()
    }
}

