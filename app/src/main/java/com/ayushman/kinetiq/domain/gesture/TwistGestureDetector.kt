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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class TwistGestureDetector(
    initialSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    customThreshold: Float? = null
) : GestureDetector<SensorReading.Gyroscope> {

    companion object {
        private const val TAG = "KinetiqTwist"
    }

    var diagnosticListener: DetectorDiagnosticListener? = null

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
    private var firstTwistDirection: Int = 0 // +1 (positive) or -1 (negative)
    private var recoilConfirmed: Boolean = false
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
            diagnosticListener?.onDetectorEvent(
                detector = "TWIST",
                eventType = "DIRECTION_REVERSAL",
                timestampNs = nowNs,
                details = "Sign reversal: vel=${String.format(Locale.US, "%.2f", primaryAngularVelocity)}, prevDispDeg=${String.format(Locale.US, "%.1f°", Math.toDegrees(integratedDisplacement.toDouble()))}, strokePeakVel=${String.format(Locale.US, "%.2f", strokePeakVelocity)} rad/s",
                metrics = mapOf(
                    "angularVelocity" to primaryAngularVelocity,
                    "displacementDeg" to Math.toDegrees(integratedDisplacement.toDouble()),
                    "strokePeakVel" to strokePeakVelocity
                )
            )
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
                    firstTwistDirection = if (integratedDisplacement >= 0f) 1 else -1
                    recoilConfirmed = false

                    Log.d(
                        TAG,
                        "First twist detected: dir=$firstTwistDirection, peakVel=$strokePeakVelocity rad/s, displacement=$absDisplacement rad"
                    )

                    diagnosticListener?.onDetectorEvent(
                        detector = "TWIST",
                        eventType = "CANDIDATE_START",
                        timestampNs = nowNs,
                        details = "First twist accepted: dir=$firstTwistDirection, peakVel=${String.format(Locale.US, "%.2f", strokePeakVelocity)} rad/s (thresh=$minAngularVelocity), dispDeg=${String.format(Locale.US, "%.1f°", Math.toDegrees(absDisplacement.toDouble()))} (thresh=${String.format(Locale.US, "%.1f°", Math.toDegrees(minAngularDisplacementRad.toDouble()))})",
                        metrics = mapOf(
                            "direction" to firstTwistDirection,
                            "peakVel" to strokePeakVelocity,
                            "minAngularVelocity" to minAngularVelocity,
                            "displacementDeg" to Math.toDegrees(absDisplacement.toDouble()),
                            "minDisplacementDeg" to Math.toDegrees(minAngularDisplacementRad.toDouble())
                        )
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
                    diagnosticListener?.onDetectorEvent(
                        detector = "TWIST",
                        eventType = "REJECTED",
                        timestampNs = nowNs,
                        details = "Double twist window expired after ${elapsedSinceFirstNs / 1_000_000}ms without second twist (recoilConfirmed=$recoilConfirmed)",
                        metrics = mapOf(
                            "elapsedMs" to elapsedSinceFirstNs / 1_000_000L,
                            "recoilConfirmed" to recoilConfirmed,
                            "reason" to "WINDOW_EXPIRED"
                        )
                    )
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

                val currentDirection = if (primaryAngularVelocity >= 0f) 1 else -1

                // 1. Recoil Verification: Must observe return motion in the opposite direction
                if (!recoilConfirmed && currentDirection != firstTwistDirection) {
                    if (absDisplacement >= Math.toRadians(4.0).toFloat()) {
                        recoilConfirmed = true
                        _state.value = currentState.copy(neutralConfirmed = true)
                        Log.d(TAG, "Twist recoil confirmed: return displacement=${Math.toDegrees(absDisplacement.toDouble())}°")
                        diagnosticListener?.onDetectorEvent(
                            detector = "TWIST",
                            eventType = "RECOIL_CONFIRMED",
                            timestampNs = nowNs,
                            details = "Twist recoil confirmed: return displacement=${String.format(Locale.US, "%.1f°", Math.toDegrees(absDisplacement.toDouble()))}",
                            metrics = mapOf("returnDispDeg" to Math.toDegrees(absDisplacement.toDouble()))
                        )
                    }
                }

                // 2. Second Twist Acceptance: ONLY after recoil is confirmed AND moving in matching twist direction
                if (recoilConfirmed && currentDirection == firstTwistDirection && elapsedSinceFirstNs >= minTwistSeparationNs) {
                    if (strokePeakVelocity >= minAngularVelocity && absDisplacement >= minAngularDisplacementRad) {
                        val durationMs = (elapsedSinceFirstNs / 1_000_000L)
                        val confidence = calculateConfidence(firstTwistPeak, strokePeakVelocity)

                        Log.i(
                            TAG,
                            "Double twist recognized! peak1=$firstTwistPeak, peak2=$strokePeakVelocity, " +
                                "duration=${durationMs}ms, confidence=$confidence"
                        )

                        diagnosticListener?.onDetectorEvent(
                            detector = "TWIST",
                            eventType = "RECOGNIZED",
                            timestampNs = nowNs,
                            details = "Double twist recognized! peak1=${String.format(Locale.US, "%.2f", firstTwistPeak)}, peak2=${String.format(Locale.US, "%.2f", strokePeakVelocity)}, duration=${durationMs}ms, confidence=${String.format(Locale.US, "%.0f%%", confidence * 100)}",
                            metrics = mapOf(
                                "peak1" to firstTwistPeak,
                                "peak2" to strokePeakVelocity,
                                "durationMs" to durationMs,
                                "confidence" to confidence
                            )
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
        firstTwistDirection = 0
        recoilConfirmed = false
        cooldownUntilNs = 0L
        lastTimestampNs = 0L
        lastAngularVelocity = 0f
        integratedDisplacement = 0f
        strokePeakVelocity = 0f
    }
}
