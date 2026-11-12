package com.ayushman.kinetiq.domain.studio.engine

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sqrt

/**
 * Hysteresis Burst Motion Segmenter with Android-Supported Dual-Sensor FIFO Flushing
 * and Bounded Replay Buffer.
 */
class MotionSegmenter(
    private val scope: CoroutineScope,
    private val sensorManager: SensorManager? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        const val A_REF = 9.80665 // 1.0 g in m/s^2
        const val W_REF = 1.0     // 1.0 rad/s
        const val ALPHA = 0.5     // Gyroscope weighting coefficient

        const val E_TRIGGER = 0.50
        const val E_SETTLE = 0.18
        const val E_REBOUND = 0.38

        const val PAUSE_WINDOW_MS = 350L
        const val QUIET_SETTLE_MS = 150L
        const val GRACE_TOTAL_MS = 300L
        const val HARD_TIMEOUT_MS = 1400L
        const val FLUSH_TIMEOUT_MS = 60L
        const val MAX_REPLAY_BUFFER_CAPACITY = 100
    }

    enum class State {
        IDLE,
        STROKE_1_ACTIVE,
        STROKE_1_PENDING,
        FLUSHING_FIFO,
        FINALIZED_SINGLE,
        STROKE_2_ACTIVE,
        SETTLING
    }

    data class SensorSample(
        val timestampNs: Long,
        val timestampMs: Long,
        val ax: Double,
        val ay: Double,
        val az: Double,
        val gx: Double,
        val gy: Double,
        val gz: Double,
        val energy: Double
    )

    private val lock = Any()
    var currentState: State = State.IDLE
        private set

    private val _candidateEvents = MutableSharedFlow<MotionCandidate>(extraBufferCapacity = 16)
    val candidateEvents: SharedFlow<MotionCandidate> = _candidateEvents.asSharedFlow()

    // Transient sample buffers for the active gesture candidate
    private val activeVectors = mutableListOf<DoubleArray>()
    private val activeTimestamps = mutableListOf<Long>()

    // Bounded replay buffer for events arriving beyond 350 ms during flush
    private val pendingReplayBuffer = ArrayDeque<SensorSample>(MAX_REPLAY_BUFFER_CAPACITY)

    // Pre-buffer rolling window (100 ms)
    private val preBuffer = ArrayDeque<SensorSample>(10)

    // Timing tracking
    private var candidateStartTimestampMs: Long = 0L
    private var stroke1EndTimestampMs: Long = 0L
    private var stroke1EndTimestampNs: Long = 0L

    // Settling tracking
    private var settlingStartTimeMs: Long = 0L
    private var quietConsecutiveMs: Long = 0L
    private var lastSampleTimestampMs: Long = 0L

    // Dual-sensor flush transaction tracking
    @Volatile
    var isAccelFlushPending: Boolean = false
        private set

    @Volatile
    var isGyroFlushPending: Boolean = false
        private set

    private var pauseTimeoutJob: Job? = null

    // Latest readings from sensors for cross-axis energy fusion
    private var lastAx = 0.0
    private var lastAy = 0.0
    private var lastAz = 0.0
    private var lastGx = 0.0
    private var lastGy = 0.0
    private var lastGz = 0.0
    private var hasAccel = false
    private var hasGyro = false

    /**
     * Dedicated listener instance registered exclusively for TYPE_LINEAR_ACCELERATION.
     */
    val accelListener: SensorEventListener2 = object : SensorEventListener2 {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                feedLinearAcceleration(
                    event.values[0].toDouble(),
                    event.values[1].toDouble(),
                    event.values[2].toDouble(),
                    event.timestamp
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onFlushCompleted(sensor: Sensor?) {
            synchronized(lock) {
                if (sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    isAccelFlushPending = false
                    checkFlushCompletionUnderLock()
                }
            }
        }
    }

    /**
     * Dedicated listener instance registered exclusively for TYPE_GYROSCOPE.
     */
    val gyroListener: SensorEventListener2 = object : SensorEventListener2 {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                feedGyroscope(
                    event.values[0].toDouble(),
                    event.values[1].toDouble(),
                    event.values[2].toDouble(),
                    event.timestamp
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onFlushCompleted(sensor: Sensor?) {
            synchronized(lock) {
                if (sensor?.type == Sensor.TYPE_GYROSCOPE) {
                    isGyroFlushPending = false
                    checkFlushCompletionUnderLock()
                }
            }
        }
    }

    fun feedLinearAcceleration(ax: Double, ay: Double, az: Double, timestampNs: Long) {
        synchronized(lock) {
            lastAx = ax
            lastAy = ay
            lastAz = az
            hasAccel = true
            processFusedSampleUnderLock(timestampNs)
        }
    }

    fun feedGyroscope(gx: Double, gy: Double, gz: Double, timestampNs: Long) {
        synchronized(lock) {
            lastGx = gx
            lastGy = gy
            lastGz = gz
            hasGyro = true
            processFusedSampleUnderLock(timestampNs)
        }
    }

    private fun processFusedSampleUnderLock(timestampNs: Long) {
        if (!hasAccel && !hasGyro) return

        val accelMag = sqrt(lastAx * lastAx + lastAy * lastAy + lastAz * lastAz)
        val gyroMag = sqrt(lastGx * lastGx + lastGy * lastGy + lastGz * lastGz)
        val energy = (accelMag / A_REF) + ALPHA * (gyroMag / W_REF)
        val timestampMs = timestampNs / 1_000_000L

        val sample = SensorSample(
            timestampNs = timestampNs,
            timestampMs = timestampMs,
            ax = lastAx,
            ay = lastAy,
            az = lastAz,
            gx = lastGx,
            gy = lastGy,
            gz = lastGz,
            energy = energy
        )

        dispatchSampleUnderLock(sample)
    }

    private fun dispatchSampleUnderLock(sample: SensorSample) {
        val dtHard = if (candidateStartTimestampMs > 0) sample.timestampMs - candidateStartTimestampMs else 0L

        // Hard Timeout Safety: Any candidate reaching 1400 ms without clean settling is strictly discarded
        if (currentState != State.IDLE && dtHard >= HARD_TIMEOUT_MS) {
            resetToIdleUnderLock()
            return
        }

        when (currentState) {
            State.IDLE -> {
                // Maintain 100 ms rolling pre-buffer
                preBuffer.addLast(sample)
                while (preBuffer.size > 5) preBuffer.removeFirst()

                if (sample.energy >= E_TRIGGER) {
                    currentState = State.STROKE_1_ACTIVE
                    candidateStartTimestampMs = sample.timestampMs
                    activeVectors.clear()
                    activeTimestamps.clear()

                    // Drain pre-buffer to preserve gesture leading edge
                    while (preBuffer.isNotEmpty()) {
                        val pb = preBuffer.removeFirst()
                        appendSampleToCandidate(pb)
                    }
                    appendSampleToCandidate(sample)
                }
            }

            State.STROKE_1_ACTIVE -> {
                appendSampleToCandidate(sample)
                if (sample.energy < E_SETTLE) {
                    currentState = State.STROKE_1_PENDING
                    stroke1EndTimestampMs = sample.timestampMs
                    stroke1EndTimestampNs = sample.timestampNs
                    startPauseTimeoutCoroutineUnderLock()
                }
            }

            State.STROKE_1_PENDING -> {
                val dtSensorMs = (sample.timestampNs - stroke1EndTimestampNs) / 1_000_000.0

                if (dtSensorMs <= PAUSE_WINDOW_MS) {
                    appendSampleToCandidate(sample)
                    if (sample.energy >= E_TRIGGER) {
                        // Stroke 2 Confirmed! Cancel timer and transition to STROKE_2_ACTIVE
                        pauseTimeoutJob?.cancel()
                        pauseTimeoutJob = null
                        currentState = State.STROKE_2_ACTIVE
                    }
                } else {
                    // Physical boundary exceeded: transition to FIFO flush
                    triggerFifoFlushingUnderLock()
                }
            }

            State.FLUSHING_FIFO -> {
                val dtSensorMs = (sample.timestampNs - stroke1EndTimestampNs) / 1_000_000.0

                if (dtSensorMs <= PAUSE_WINDOW_MS) {
                    appendSampleToCandidate(sample)
                    if (sample.energy >= E_TRIGGER) {
                        // Late burst in FIFO detected
                        pauseTimeoutJob?.cancel()
                        pauseTimeoutJob = null
                        isAccelFlushPending = false
                        isGyroFlushPending = false
                        currentState = State.STROKE_2_ACTIVE
                    }
                } else {
                    // Event is beyond 350 ms boundary: route to pendingReplayBuffer
                    if (pendingReplayBuffer.size >= MAX_REPLAY_BUFFER_CAPACITY) {
                        // Safe Overflow: discard ambiguous data rather than corrupting boundaries
                        resetToIdleUnderLock()
                    } else {
                        pendingReplayBuffer.addLast(sample)
                    }
                }
            }

            State.STROKE_2_ACTIVE -> {
                appendSampleToCandidate(sample)
                if (sample.energy < E_SETTLE) {
                    currentState = State.SETTLING
                    settlingStartTimeMs = sample.timestampMs
                    quietConsecutiveMs = 0L
                    lastSampleTimestampMs = sample.timestampMs
                }
            }

            State.SETTLING -> {
                val dtGrace = sample.timestampMs - settlingStartTimeMs
                val sampleDeltaMs = (sample.timestampMs - lastSampleTimestampMs).coerceAtLeast(0L)
                lastSampleTimestampMs = sample.timestampMs

                when {
                    sample.energy >= E_REBOUND -> {
                        // Secondary strike or active rebound -> Discard
                        resetToIdleUnderLock()
                    }
                    sample.energy < E_SETTLE -> {
                        // Accumulate quiet time
                        quietConsecutiveMs += sampleDeltaMs
                        appendSampleToCandidate(sample)

                        if (quietConsecutiveMs >= QUIET_SETTLE_MS) {
                            emitCandidateUnderLock(strokeCount = 2, hasPause = true)
                            resetToIdleUnderLock()
                        }
                    }
                    else -> {
                        // Residual noise (0.18 <= E_t < 0.38): reset quiet time, grace continues
                        quietConsecutiveMs = 0L
                        appendSampleToCandidate(sample)

                        if (dtGrace >= GRACE_TOTAL_MS) {
                            // Total grace ceiling expired without settling
                            resetToIdleUnderLock()
                        }
                    }
                }
            }

            State.FINALIZED_SINGLE -> {
                // Handled in transition logic
            }
        }
    }

    private fun startPauseTimeoutCoroutineUnderLock() {
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = scope.launch(dispatcher) {
            delay(PAUSE_WINDOW_MS)
            synchronized(lock) {
                if (currentState == State.STROKE_1_PENDING) {
                    triggerFifoFlushingUnderLock()
                }
            }
        }
    }

    private fun triggerFifoFlushingUnderLock() {
        currentState = State.FLUSHING_FIFO

        if (sensorManager == null) {
            // JVM / Mock test fallback: simulate flush completion immediately
            isAccelFlushPending = false
            isGyroFlushPending = false
            checkFlushCompletionUnderLock()
            return
        }

        // Trigger independent flushes on separate listener instances
        val accelAccepted = sensorManager.flush(accelListener)
        val gyroAccepted = sensorManager.flush(gyroListener)

        if (!accelAccepted || !gyroAccepted) {
            // EITHER sensor does not support FIFO flushing or driver is busy
            // Safety policy: discard ambiguous candidate immediately
            resetToIdleUnderLock()
            return
        }

        isAccelFlushPending = true
        isGyroFlushPending = true

        // Bounded asynchronous timeout: 60 ms
        scope.launch(dispatcher) {
            val completed = withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
                while (isAccelFlushPending || isGyroFlushPending) {
                    delay(5L)
                }
                true
            }

            synchronized(lock) {
                if (completed != true && currentState == State.FLUSHING_FIFO) {
                    // Flush timed out past 60 ms: safe discard
                    resetToIdleUnderLock()
                }
            }
        }
    }

    private fun checkFlushCompletionUnderLock() {
        if (!isAccelFlushPending && !isGyroFlushPending && currentState == State.FLUSHING_FIFO) {
            // Both flushes completed successfully and all buffered FIFO events were processed!
            val duration = (stroke1EndTimestampMs - candidateStartTimestampMs).coerceAtLeast(0L)

            if (duration >= 350L) {
                // Emit valid single-stroke candidate
                emitCandidateUnderLock(strokeCount = 1, hasPause = false)
            }

            // Transition to IDLE and chronologically replay preserved events
            currentState = State.IDLE
            activeVectors.clear()
            activeTimestamps.clear()
            candidateStartTimestampMs = 0L

            replayBufferedEventsUnderLock()
        }
    }

    private fun replayBufferedEventsUnderLock() {
        if (pendingReplayBuffer.isEmpty()) return

        val replayList = ArrayList(pendingReplayBuffer)
        pendingReplayBuffer.clear()

        for (sample in replayList) {
            dispatchSampleUnderLock(sample)
            if (currentState != State.IDLE) {
                // If a replayed sample triggered STROKE_1_ACTIVE for Candidate 2,
                // continue feeding remaining samples into the new candidate window
            }
        }
    }

    private fun appendSampleToCandidate(sample: SensorSample) {
        activeVectors.add(
            doubleArrayOf(sample.ax, sample.ay, sample.az, sample.gx, sample.gy, sample.gz)
        )
        activeTimestamps.add(sample.timestampMs)
    }

    private fun emitCandidateUnderLock(strokeCount: Int, hasPause: Boolean) {
        if (activeVectors.size < 2) return

        val candidate = MotionCandidate(
            vectors = ArrayList(activeVectors),
            timestampsMs = ArrayList(activeTimestamps),
            strokeCount = strokeCount,
            hasInterStrokePause = hasPause,
            durationMs = activeTimestamps.last() - activeTimestamps.first(),
            physicalEndTimestampMs = activeTimestamps.last()
        )
        _candidateEvents.tryEmit(candidate)
    }

    private fun resetToIdleUnderLock() {
        currentState = State.IDLE
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = null
        isAccelFlushPending = false
        isGyroFlushPending = false
        activeVectors.clear()
        activeTimestamps.clear()
        pendingReplayBuffer.clear()
        candidateStartTimestampMs = 0L
        stroke1EndTimestampMs = 0L
        stroke1EndTimestampNs = 0L
        quietConsecutiveMs = 0L
    }

    fun reset() {
        synchronized(lock) {
            resetToIdleUnderLock()
            hasAccel = false
            hasGyro = false
        }
    }
}

