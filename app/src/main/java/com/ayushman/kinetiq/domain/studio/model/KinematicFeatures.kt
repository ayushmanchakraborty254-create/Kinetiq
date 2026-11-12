package com.ayushman.kinetiq.domain.studio.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Supplementary kinematic intensity features for a candidate or template.
 *
 * Evaluates peak movement intensity, total duration, and inter-stroke interval
 * as continuous soft matching features. Single-stroke gestures have pauseMs = null
 * and completely omit pause from distance calculations (never substituting zero).
 */
data class KinematicFeatures(
    val peakLinearAcceleration: Double,
    val peakAngularVelocity: Double,
    val totalDurationMs: Long,
    val interStrokePauseMs: Long?
) {
    companion object {
        // Physical variance floors to protect against zero-variance / overfitting
        const val FLOOR_STD_DEV_ACCEL = 1.50   // m/s^2
        const val FLOOR_STD_DEV_GYRO = 0.50    // rad/s
        const val FLOOR_STD_DEV_DURATION = 50.0 // ms
        const val FLOOR_STD_DEV_PAUSE = 30.0   // ms

        /**
         * Extracts kinematic features from a segmented candidate.
         */
        fun extract(candidate: MotionCandidate, pauseDurationMs: Long? = null): KinematicFeatures {
            var maxAccelMagSq = 0.0
            var maxGyroMagSq = 0.0

            for (vec in candidate.vectors) {
                val ax = vec[0]
                val ay = vec[1]
                val az = vec[2]
                val gx = vec[3]
                val gy = vec[4]
                val gz = vec[5]

                val accelMagSq = ax * ax + ay * ay + az * az
                val gyroMagSq = gx * gx + gy * gy + gz * gz

                if (accelMagSq > maxAccelMagSq) maxAccelMagSq = accelMagSq
                if (gyroMagSq > maxGyroMagSq) maxGyroMagSq = gyroMagSq
            }

            return KinematicFeatures(
                peakLinearAcceleration = sqrt(maxAccelMagSq),
                peakAngularVelocity = sqrt(maxGyroMagSq),
                totalDurationMs = candidate.durationMs,
                interStrokePauseMs = if (candidate.strokeCount > 1) (pauseDurationMs ?: candidate.durationMs / 4) else null
            )
        }
    }

    /**
     * Calculates the compatible kinematic distance between this candidate and a target template.
     *
     * @param target Kinematic features of the target template.
     * @param stdDevAccel Effective standard deviation for peak acceleration.
     * @param stdDevGyro Effective standard deviation for peak angular velocity.
     * @param stdDevDuration Effective standard deviation for total duration.
     * @param stdDevPause Effective standard deviation for inter-stroke pause (used only if double-stroke).
     * @return Path-normalized kinematic distance.
     */
    fun distanceTo(
        target: KinematicFeatures,
        stdDevAccel: Double,
        stdDevGyro: Double,
        stdDevDuration: Double,
        stdDevPause: Double
    ): Double {
        val effSdAccel = max(stdDevAccel, FLOOR_STD_DEV_ACCEL)
        val effSdGyro = max(stdDevGyro, FLOOR_STD_DEV_GYRO)
        val effSdDuration = max(stdDevDuration, FLOOR_STD_DEV_DURATION)

        val dAccel = abs(peakLinearAcceleration - target.peakLinearAcceleration) / effSdAccel
        val dGyro = abs(peakAngularVelocity - target.peakAngularVelocity) / effSdGyro
        val dDuration = abs(totalDurationMs - target.totalDurationMs).toDouble() / effSdDuration

        return if (interStrokePauseMs != null && target.interStrokePauseMs != null) {
            // Double-stroke: 4 features normalized by 1/4
            val effSdPause = max(stdDevPause, FLOOR_STD_DEV_PAUSE)
            val dPause = abs(interStrokePauseMs - target.interStrokePauseMs).toDouble() / effSdPause
            0.25 * (dAccel + dGyro + dDuration + dPause)
        } else {
            // Single-stroke: 3 features normalized by 1/3 (pause omitted, never substituted with 0)
            (1.0 / 3.0) * (dAccel + dGyro + dDuration)
        }
    }
}

