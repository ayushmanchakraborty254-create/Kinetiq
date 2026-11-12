package com.ayushman.kinetiq.domain.studio.engine

import com.ayushman.kinetiq.domain.studio.model.KinematicBaselines
import com.ayushman.kinetiq.domain.studio.model.KinematicFeatures
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Calibration and Medoid extraction engine.
 * Computes thresholds and statistical baselines strictly on the 8 training repetitions.
 */
object CalibrationEngine {

    const val MAX_POSITIVE_THRESHOLD_CEILING = 12.0
    const val MAX_ACCEPTABLE_CLUSTER_VARIANCE = 3.5

    data class CalibrationResult(
        val medoid: MotionCandidate,
        val medoidIndex: Int,
        val thresholdPos: Double,
        val clusterMean: Double,
        val clusterStdDev: Double,
        val kinematicBaselines: KinematicBaselines
    )

    sealed class CalibrationOutcome {
        data class Success(val result: CalibrationResult) : CalibrationOutcome()
        data class InconsistentRepetitions(val stdDev: Double, val message: String) : CalibrationOutcome()
    }

    /**
     * Executes positive cluster calibration on 8 positive training repetitions.
     */
    fun calibrateTrainingSet(trainingSamples: List<MotionCandidate>): CalibrationOutcome {
        require(trainingSamples.size >= 8) {
            "Calibration requires at least 8 positive training repetitions (received ${trainingSamples.size})"
        }
        val samples = trainingSamples.take(8)

        // 1. Compute Pairwise Distance Matrix
        val n = samples.size
        val distMatrix = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val d = DynamicTimeWarping.computeDistance(samples[i], samples[j])
                distMatrix[i][j] = d
                distMatrix[j][i] = d
            }
        }

        // 2. Medoid Selection: index minimizing sum of distances to all other training repetitions
        var bestIndex = 0
        var minSumDist = Double.POSITIVE_INFINITY
        for (i in 0 until n) {
            var sumDist = 0.0
            for (j in 0 until n) {
                sumDist += distMatrix[i][j]
            }
            if (sumDist < minSumDist) {
                minSumDist = sumDist
                bestIndex = i
            }
        }
        val medoid = samples[bestIndex]

        // 3. Cluster Radii: Distances from medoid to all training samples
        val radii = DoubleArray(n) { j -> distMatrix[bestIndex][j] }

        // 4. Mean and Sample Standard Deviation
        var sumR = 0.0
        for (r in radii) sumR += r
        val meanR = sumR / n

        var sumSqDiff = 0.0
        for (r in radii) {
            val diff = r - meanR
            sumSqDiff += diff * diff
        }
        val stdDevR = sqrt(sumSqDiff / (n - 1))

        // 5. Inconsistency Guard
        if (stdDevR > MAX_ACCEPTABLE_CLUSTER_VARIANCE) {
            return CalibrationOutcome.InconsistentRepetitions(
                stdDev = stdDevR,
                message = "Inconsistent repetitions (σ = ${String.format("%.2f", stdDevR)} > $MAX_ACCEPTABLE_CLUSTER_VARIANCE). Please record your movements with consistent force."
            )
        }

        // 6. Threshold Determination: T_pos = μ + 2.5 * max(σ, 0.5), capped at 12.0
        val thresholdPos = min(
            MAX_POSITIVE_THRESHOLD_CEILING,
            meanR + 2.5 * max(stdDevR, 0.5)
        )

        // 7. Kinematic Baselines Extraction
        val kinBaselines = extractKinematicBaselines(samples)

        return CalibrationOutcome.Success(
            CalibrationResult(
                medoid = medoid,
                medoidIndex = bestIndex,
                thresholdPos = thresholdPos,
                clusterMean = meanR,
                clusterStdDev = stdDevR,
                kinematicBaselines = kinBaselines
            )
        )
    }

    /**
     * Metric-consistent conflict advisory check between two medoid profiles.
     * Evaluates whether D_cross < σ_pos(A) + σ_pos(B).
     */
    fun checkConflictAdvisory(
        medoidA: MotionCandidate,
        stdDevA: Double,
        gestureAName: String,
        medoidB: MotionCandidate,
        stdDevB: Double,
        gestureBName: String
    ): String? {
        val dCross = DynamicTimeWarping.computeDistance(medoidA, medoidB)
        val conflictThreshold = stdDevA + stdDevB

        return if (dCross < conflictThreshold) {
            "Conflict Advisory (Heuristic): Motion for '$gestureAName' may have similar dynamics to '$gestureBName' (Distance ${String.format("%.2f", dCross)} < Threshold ${String.format("%.2f", conflictThreshold)}). Please verify distinction during hold-out testing."
        } else {
            null
        }
    }

    private fun extractKinematicBaselines(samples: List<MotionCandidate>): KinematicBaselines {
        val n = samples.size.toDouble()
        val kinList = samples.map { KinematicFeatures.extract(it) }

        val meanAccel = kinList.sumOf { it.peakLinearAcceleration } / n
        val meanGyro = kinList.sumOf { it.peakAngularVelocity } / n
        val meanDur = kinList.sumOf { it.totalDurationMs.toDouble() } / n

        var sqDiffAccel = 0.0
        var sqDiffGyro = 0.0
        var sqDiffDur = 0.0
        for (k in kinList) {
            val dAcc = k.peakLinearAcceleration - meanAccel
            sqDiffAccel += dAcc * dAcc

            val dGyr = k.peakAngularVelocity - meanGyro
            sqDiffGyro += dGyr * dGyr

            val dDur = k.totalDurationMs.toDouble() - meanDur
            sqDiffDur += dDur * dDur
        }
        val sdAccel = max(sqrt(sqDiffAccel / (n - 1)), KinematicFeatures.FLOOR_STD_DEV_ACCEL)
        val sdGyro = max(sqrt(sqDiffGyro / (n - 1)), KinematicFeatures.FLOOR_STD_DEV_GYRO)
        val sdDur = max(sqrt(sqDiffDur / (n - 1)), KinematicFeatures.FLOOR_STD_DEV_DURATION)

        val pauses = kinList.mapNotNull { it.interStrokePauseMs?.toDouble() }
        val (meanPause, sdPause) = if (pauses.size == samples.size) {
            val pMean = pauses.sum() / n
            var pSqDiff = 0.0
            for (p in pauses) {
                val diff = p - pMean
                pSqDiff += diff * diff
            }
            val pSd = max(sqrt(pSqDiff / (n - 1)), KinematicFeatures.FLOOR_STD_DEV_PAUSE)
            Pair(pMean, pSd)
        } else {
            Pair(null, null)
        }

        return KinematicBaselines(
            peakAccelMean = meanAccel,
            peakAccelStdDev = sdAccel,
            peakGyroMean = meanGyro,
            peakGyroStdDev = sdGyro,
            durationMeanMs = meanDur,
            durationStdDevMs = sdDur,
            pauseMeanMs = meanPause,
            pauseStdDevMs = sdPause
        )
    }
}
