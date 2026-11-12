package com.ayushman.kinetiq.domain.studio.engine

import com.ayushman.kinetiq.domain.studio.model.KinematicFeatures
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Multi-Channel Dynamic Time Warping (MC-DTW) with Slanted Sakoe-Chiba Band,
 * Path-Length Normalization, and Strict Diagonal Tie-Breaking.
 */
object DynamicTimeWarping {

    const val KINEMATIC_PENALTY_WEIGHT = 0.20
    private const val MIN_ACCEL_SCALE = 2.0  // m/s^2 floor
    private const val MIN_GYRO_SCALE = 0.5   // rad/s floor

    /**
     * Computes the combined DTW distance between a candidate and a template.
     *
     * @param candidate Incoming motion candidate.
     * @param template Trained reference template candidate.
     * @param stdDevAccel Feature std dev for acceleration.
     * @param stdDevGyro Feature std dev for gyroscope.
     * @param stdDevDuration Feature std dev for total duration.
     * @param stdDevPause Feature std dev for pause (nullable).
     * @return Path-normalized combined distance D_total, or POSITIVE_INFINITY if endpoint is unreachable.
     */
    fun computeDistance(
        candidate: MotionCandidate,
        template: MotionCandidate,
        stdDevAccel: Double = KinematicFeatures.FLOOR_STD_DEV_ACCEL,
        stdDevGyro: Double = KinematicFeatures.FLOOR_STD_DEV_GYRO,
        stdDevDuration: Double = KinematicFeatures.FLOOR_STD_DEV_DURATION,
        stdDevPause: Double = KinematicFeatures.FLOOR_STD_DEV_PAUSE
    ): Double {
        val n = candidate.sampleCount
        val m = template.sampleCount

        // Structural guards
        if (n < 2 || m < 2) return Double.POSITIVE_INFINITY

        // 1. Independent Family-Specific Normalization
        val normC = normalizeCandidate(candidate)
        val normT = normalizeCandidate(template)

        // 2. Slanted Sakoe-Chiba Band Radius
        val maxDim = max(n, m)
        val r = max(4, floor(0.15 * maxDim).toInt())

        // 3. DP Accumulated Distance Matrix (1-indexed for convenience)
        val dp = Array(n + 1) { DoubleArray(m + 1) { Double.POSITIVE_INFINITY } }
        dp[0][0] = 0.0

        val slope = (n - 1).toDouble() / (m - 1).toDouble()

        for (i in 1..n) {
            val centerJ = ((i - 1) / slope) + 1.0
            val minJ = max(1, floor(centerJ - r).toInt())
            val maxJ = min(m, floor(centerJ + r).toInt())

            for (j in minJ..maxJ) {
                // Verify slanted band condition |(i - 1) - slope * (j - 1)| <= r
                val bandDist = abs((i - 1) - slope * (j - 1))
                if (bandDist > r) continue

                val cost = localEuclideanDistance(normC[i - 1], normT[j - 1])

                // Predecessor cells
                val diag = dp[i - 1][j - 1]
                val left = dp[i][j - 1]
                val up = dp[i - 1][j]

                // Diagonal tie-breaking: diag > left > up
                val minPrev = when {
                    diag <= left && diag <= up -> diag
                    left <= up -> left
                    else -> up
                }

                if (minPrev != Double.POSITIVE_INFINITY) {
                    dp[i][j] = cost + minPrev
                }
            }
        }

        val totalCost = dp[n][m]
        if (totalCost == Double.POSITIVE_INFINITY || totalCost.isNaN()) {
            return Double.POSITIVE_INFINITY // Endpoint unreachable within band
        }

        // 4. Backtracking to compute exact path step count K
        val k = backtrackPathLength(dp, n, m, slope, r)
        val normalizedDtwDistance = totalCost / max(1, k)

        // 5. Kinematic Distance Penalty
        val kinC = KinematicFeatures.extract(candidate)
        val kinT = KinematicFeatures.extract(template)
        val kinDist = kinC.distanceTo(kinT, stdDevAccel, stdDevGyro, stdDevDuration, stdDevPause)

        return normalizedDtwDistance * (1.0 + KINEMATIC_PENALTY_WEIGHT * kinDist)
    }

    /**
     * Normalizes linear acceleration and gyroscope components independently using peak magnitude.
     */
    private fun normalizeCandidate(candidate: MotionCandidate): Array<DoubleArray> {
        var maxAccelMag = 0.0
        var maxGyroMag = 0.0

        for (v in candidate.vectors) {
            val aMag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            val gMag = sqrt(v[3] * v[3] + v[4] * v[4] + v[5] * v[5])
            if (aMag > maxAccelMag) maxAccelMag = aMag
            if (gMag > maxGyroMag) maxGyroMag = gMag
        }

        val scaleA = max(maxAccelMag, MIN_ACCEL_SCALE)
        val scaleG = max(maxGyroMag, MIN_GYRO_SCALE)

        return Array(candidate.sampleCount) { idx ->
            val src = candidate.vectors[idx]
            doubleArrayOf(
                src[0] / scaleA,
                src[1] / scaleA,
                src[2] / scaleA,
                src[3] / scaleG,
                src[4] / scaleG,
                src[5] / scaleG
            )
        }
    }

    private fun localEuclideanDistance(v1: DoubleArray, v2: DoubleArray): Double {
        var sumSq = 0.0
        for (i in 0..5) {
            val diff = v1[i] - v2[i]
            sumSq += diff * diff
        }
        return sqrt(sumSq)
    }

    private fun backtrackPathLength(
        dp: Array<DoubleArray>,
        n: Int,
        m: Int,
        slope: Double,
        r: Int
    ): Int {
        var currI = n
        var currJ = m
        var steps = 1

        while (currI > 1 || currJ > 1) {
            if (currI == 1) {
                currJ--
                steps++
                continue
            }
            if (currJ == 1) {
                currI--
                steps++
                continue
            }

            val diag = dp[currI - 1][currJ - 1]
            val left = dp[currI][currJ - 1]
            val up = dp[currI - 1][currJ]

            // Strict diagonal tie-break: diag > left > up
            when {
                diag <= left && diag <= up -> {
                    currI--
                    currJ--
                }
                left <= up -> {
                    currJ--
                }
                else -> {
                    currI--
                }
            }
            steps++
        }
        return steps
    }
}

