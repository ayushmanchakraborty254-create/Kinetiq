package com.ayushman.kinetiq.domain.studio.model

/**
 * A segmented candidate movement emitted by the MotionSegmenter.
 *
 * @param vectors List of 6-channel sensor vectors [ax, ay, az, gx, gy, gz].
 *                Linear acceleration in m/s^2, Angular velocity in rad/s.
 * @param timestampsMs Monotonic hardware timestamps for each sample in milliseconds.
 * @param strokeCount Physical stroke count determined by segmenter (1 for single, 2 for double).
 * @param hasInterStrokePause Whether a distinct inter-stroke deceleration pause was detected.
 * @param durationMs Total duration of the candidate movement in milliseconds.
 * @param physicalEndTimestampMs Monotonic hardware timestamp at the instant deceleration completed.
 */
data class MotionCandidate(
    val vectors: List<DoubleArray>,
    val timestampsMs: List<Long>,
    val strokeCount: Int,
    val hasInterStrokePause: Boolean,
    val durationMs: Long,
    val physicalEndTimestampMs: Long
) {
    init {
        require(vectors.size == timestampsMs.size) {
            "Vectors and timestamps must have identical lengths (vectors=${vectors.size}, timestamps=${timestampsMs.size})"
        }
        require(vectors.all { it.size == 6 }) {
            "Each sensor vector must contain exactly 6 channels (ax, ay, az, gx, gy, gz)"
        }
    }

    val sampleCount: Int get() = vectors.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MotionCandidate
        if (strokeCount != other.strokeCount) return false
        if (hasInterStrokePause != other.hasInterStrokePause) return false
        if (durationMs != other.durationMs) return false
        if (physicalEndTimestampMs != other.physicalEndTimestampMs) return false
        if (timestampsMs != other.timestampsMs) return false
        if (vectors.size != other.vectors.size) return false
        for (i in vectors.indices) {
            if (!vectors[i].contentEquals(other.vectors[i])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = strokeCount
        result = 31 * result + hasInterStrokePause.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + physicalEndTimestampMs.hashCode()
        result = 31 * result + timestampsMs.hashCode()
        result = 31 * result + vectors.size
        return result
    }
}

