package com.ayushman.kinetiq.domain.studio

import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import kotlin.math.PI
import kotlin.math.sin

object StudioTestHelper {

    fun createCandidate(
        sampleCount: Int = 20,
        peakAccel: Double = 15.0,
        peakGyro: Double = 5.0,
        strokeCount: Int = 2,
        hasPause: Boolean = true,
        durationMs: Long = 600L,
        startEpochMs: Long = 1000L
    ): MotionCandidate {
        val vectors = ArrayList<DoubleArray>(sampleCount)
        val timestamps = ArrayList<Long>(sampleCount)
        val stepMs = durationMs / (sampleCount - 1).coerceAtLeast(1)

        for (i in 0 until sampleCount) {
            val progress = i.toDouble() / (sampleCount - 1).toDouble()
            val phase = progress * 2 * PI
            val ax = 0.0
            val ay = peakAccel * sin(phase)
            val az = peakAccel * 0.5 * sin(phase)
            val gx = peakGyro * sin(phase)
            val gy = 0.0
            val gz = 0.0

            vectors.add(doubleArrayOf(ax, ay, az, gx, gy, gz))
            timestamps.add(startEpochMs + (i * stepMs))
        }

        return MotionCandidate(
            vectors = vectors,
            timestampsMs = timestamps,
            strokeCount = strokeCount,
            hasInterStrokePause = hasPause,
            durationMs = durationMs,
            physicalEndTimestampMs = timestamps.last()
        )
    }

    fun createNoiseCandidate(
        sampleCount: Int = 15,
        durationMs: Long = 400L,
        startEpochMs: Long = 2000L
    ): MotionCandidate {
        val vectors = ArrayList<DoubleArray>(sampleCount)
        val timestamps = ArrayList<Long>(sampleCount)
        val stepMs = durationMs / (sampleCount - 1).coerceAtLeast(1)

        for (i in 0 until sampleCount) {
            // Low amplitude random-like motion
            val ax = 0.5
            val ay = 1.0
            val az = 0.2
            val gx = 0.1
            val gy = 0.2
            val gz = 0.1

            vectors.add(doubleArrayOf(ax, ay, az, gx, gy, gz))
            timestamps.add(startEpochMs + (i * stepMs))
        }

        return MotionCandidate(
            vectors = vectors,
            timestampsMs = timestamps,
            strokeCount = 1,
            hasInterStrokePause = false,
            durationMs = durationMs,
            physicalEndTimestampMs = timestamps.last()
        )
    }
}

