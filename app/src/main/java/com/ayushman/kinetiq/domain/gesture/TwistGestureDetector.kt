package com.ayushman.kinetiq.domain.gesture

import android.util.Log
import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureState
import com.ayushman.kinetiq.domain.model.GestureType
import com.ayushman.kinetiq.domain.model.SensitivityLevel
import com.ayushman.kinetiq.domain.model.SensorReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

class TwistGestureDetector(
    initialSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    customThreshold: Float? = null
) : GestureDetector<SensorReading.Gyroscope> {

    companion object {
        private const val TAG = "KinetiqTwist"
    }

    var sensitivity: SensitivityLevel = initialSensitivity
        set(value) {
            field = value
            updateThresholds()
        }

    var customVelocityThresholdOverride: Float? = customThreshold
        set(value) {
            field = value
            updateThresholds()
        }

    // Dynamic thresholds (PRESERVED: DO NOT ALTER WORKING SENSITIVITY)
    private var minAngularVelocity: Float = 4.2f // rad/s
    private var minAngularDisplacementRad: Float = Math.toRadians(28.0).toFloat() // radians (~28 deg)

    // Timing constants in nanoseconds
    private val minTwistSeparationNs: Long = 140_000_000L // 140ms min separation between twists
    private val maxWindowDurationNs: Long = 800_000_000L // 800ms window for double twist
    private val cooldownDurationNs: Long = 1_200_000_000L // 1200ms cooldown after launch

    private val _state = MutableStateFlow<GestureState>(GestureState.Idle)
    override val state: StateFlow<GestureState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GestureEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<GestureEvent> = _events.asSharedFlow()

    // Tracking variables for numerical integration
    private var lastTimestampNs: Long = 0L
    private var lastAngularVelocity: Float = 0f
    private var integratedDisplacement: Float = 0f
    private var strokePeakVelocity: Float = 0f

    // State machine timestamps
    private var firstTwistTimestampNs: Long = 0L
    private var firstTwistPeak: Float = 0f
    private var cooldownUntilNs: Long = 0L

    init {
        updateThresholds()
    }

    private fun updateThresholds() {
        minAngularVelocity = customVelocityThresholdOverride ?: sensitivity.twistAngularVelocityThreshold
        minAngularDisplacementRad = sensitivity.twistMinDisplacementRad
    }

    override fun processReading(reading: SensorReading.Gyroscope) {
        val nowNs = reading.timestampNs

        if (lastTimestampNs == 0L) {
            lastTimestampNs = nowNs
            lastAngularVelocity = reading.z
            return
        }

        // Time delta in seconds
        val dt = (nowNs - lastTimestampNs) / 1_000_000_000.0f
        lastTimestampNs = nowNs

        // Ignore large gaps (e.g. sensor was paused)
        if (dt <= 0f || dt > 0.15f) {
            lastAngularVelocity = reading.z
            integratedDisplacement = 0f
            return
        }

        // The dominant axis for a wrist twist is rotation along the phone's long axis (y) or screen axis (z).
        // Select the axis with higher angular velocity magnitude:
        val primaryAngularVelocity = if (abs(reading.z) >= abs(reading.y)) reading.z else reading.y

        // Trapezoidal integration for angular displacement: deltaTheta = ((w0 + w1) / 2) * dt
        val dTheta = ((lastAngularVelocity + primaryAngularVelocity) / 2.0f) * dt
        lastAngularVelocity = primaryAngularVelocity

        // Integrate displacement while movement continues in same direction; reset on sign reversal
        if ((integratedDisplacement > 0 && primaryAngularVelocity < 0) ||
            (integratedDisplacement < 0 && primaryAngularVelocity > 0)
        ) {
            // Reversal detected (user twisting back)
            integratedDisplacement = dTheta
            strokePeakVelocity = abs(primaryAngularVelocity)
        } else {
            integratedDisplacement += dTheta
            strokePeakVelocity = max(strokePeakVelocity, abs(primaryAngularVelocity))
        }

        val absDisplacement = abs(integratedDisplacement)

        when (val currentState = _state.value) {
            is GestureState.Idle -> {
                // First twist detection: both velocity AND displacement thresholds must be crossed
                if (strokePeakVelocity >= minAngularVelocity && absDisplacement >= minAngularDisplacementRad) {
                    firstTwistTimestampNs = nowNs
                    firstTwistPeak = strokePeakVelocity

                    Log.d(
                        TAG,
                        "First twist detected: peakVel=$strokePeakVelocity rad/s, displacement=$absDisplacement rad"
                    )

                    _state.value = GestureState.FirstMovementDetected(
                        timestampNs = nowNs,
                        peakMagnitude = strokePeakVelocity
                    )

                    _state.value = GestureState.WaitingForSecondMovement(
                        firstTimestampNs = nowNs,
                        deadlineNs = nowNs + maxWindowDurationNs,
                        neutralConfirmed = false
                    )

                    // Reset integrator for next stroke
                    integratedDisplacement = 0f
                    strokePeakVelocity = 0f
                }
            }

            is GestureState.WaitingForSecondMovement -> {
                val elapsedSinceFirstNs = nowNs - firstTwistTimestampNs

                if (elapsedSinceFirstNs > maxWindowDurationNs) {
                    Log.d(TAG, "Double twist window expired after ${elapsedSinceFirstNs / 1_000_000}ms")
                    _events.tryEmit(
                        GestureEvent.Rejected(
                            type = GestureType.DOUBLE_TWIST,
                            reason = "Window expired without second twist",
                            timestampNs = nowNs
                        )
                    )
                    reset()
                    return
                }

                // Check for second twist after minimum separation
                if (elapsedSinceFirstNs >= minTwistSeparationNs) {
                    if (strokePeakVelocity >= minAngularVelocity && absDisplacement >= minAngularDisplacementRad) {
                        val durationMs = (elapsedSinceFirstNs / 1_000_000L)
                        val confidence = calculateConfidence(firstTwistPeak, strokePeakVelocity)

                        Log.i(
                            TAG,
                            "Double twist recognized! peak1=$firstTwistPeak, peak2=$strokePeakVelocity, " +
                                "duration=${durationMs}ms, confidence=$confidence"
                        )

                        _state.value = GestureState.SecondMovementDetected(
                            timestampNs = nowNs,
                            peakMagnitude = strokePeakVelocity
                        )

                        _events.tryEmit(
                            GestureEvent.Recognized(
                                type = GestureType.DOUBLE_TWIST,
                                timestampNs = nowNs,
                                confidence = confidence,
                                executionDurationMs = durationMs
                            )
                        )

                        cooldownUntilNs = nowNs + cooldownDurationNs
                        _state.value = GestureState.Cooldown(untilNs = cooldownUntilNs)
                    }
                }
            }

            is GestureState.Cooldown -> {
                if (nowNs >= currentState.untilNs) {
                    Log.d(TAG, "Twist cooldown completed, returning to Idle")
                    reset()
                }
            }

            is GestureState.FirstMovementDetected,
            is GestureState.SecondMovementDetected -> {
                // Handled above
            }
        }
    }

    private fun calculateConfidence(peak1: Float, peak2: Float): Float {
        val ratio = if (peak1 > 0f && peak2 > 0f) {
            val minP = kotlin.math.min(peak1, peak2)
            val maxP = max(peak1, peak2)
            minP / maxP
        } else 0.5f

        val velocityRatio = ((peak1 + peak2) / (2f * minAngularVelocity)).coerceIn(0.8f, 1.3f)
        return (ratio * 0.7f + velocityRatio * 0.3f).coerceIn(0.1f, 1.0f)
    }

    override fun reset() {
        _state.value = GestureState.Idle
        firstTwistTimestampNs = 0L
        firstTwistPeak = 0f
        cooldownUntilNs = 0L
        lastTimestampNs = 0L
        lastAngularVelocity = 0f
        integratedDisplacement = 0f
        strokePeakVelocity = 0f
    }
}
