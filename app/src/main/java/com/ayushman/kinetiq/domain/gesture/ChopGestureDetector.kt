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
import kotlin.math.max
import kotlin.math.min

class ChopGestureDetector(
    initialSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM,
    customThreshold: Float? = null
) : GestureDetector<SensorReading.LinearAcceleration> {

    companion object {
        private const val TAG = "KinetiqChop"
    }

    var diagnosticListener: DetectorDiagnosticListener? = null

    var sensitivity: SensitivityLevel = initialSensitivity
        set(value) {
            field = value
            updateThresholds()
        }

    var customPeakThresholdOverride: Float? = customThreshold
        set(value) {
            field = value
            updateThresholds()
        }

    // Dynamic thresholds derived from sensitivity or calibration
    private var peakThreshold: Float = 13.5f
    private var neutralThreshold: Float = 3.8f

    // Timing constants in nanoseconds
    private val minPeakSeparationNs: Long = 120_000_000L // 120ms min separation between distinct chop strokes
    private val maxWindowDurationNs: Long = 800_000_000L // 800ms max window for double chop
    private val cooldownDurationNs: Long = 1_000_000_000L // 1000ms cooldown after successful gesture

    // Signal smoothing: responsive low-pass filter (preserves sharp impulse peaks)
    private var lastSmoothedMagnitude: Float = 0f

    private val _state = MutableStateFlow<GestureState>(GestureState.Idle)
    override val state: StateFlow<GestureState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GestureEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<GestureEvent> = _events.asSharedFlow()

    // State tracking variables
    private var firstChopTimestampNs: Long = 0L
    private var firstChopPeak: Float = 0f
    private var neutralConfirmed: Boolean = false
    private var neutralConfirmedTimestampNs: Long = 0L
    private var cooldownUntilNs: Long = 0L

    init {
        updateThresholds()
    }

    private fun updateThresholds() {
        peakThreshold = customPeakThresholdOverride ?: sensitivity.chopPeakThreshold
        neutralThreshold = sensitivity.chopNeutralThreshold
    }

    override fun processReading(reading: SensorReading.LinearAcceleration) {
        val nowNs = reading.timestampNs
        val rawMagnitude = reading.magnitude

        // 1. Responsive signal smoothing:
        // Weight 85% current sample + 15% previous sample.
        // This removes single-sample hardware glitch/jitter while preserving 85%+ of genuine
        // 20-40ms physical chop impulse peaks (unlike a 3-sample moving average which dampened peaks by >50%).
        val smoothedMagnitude = if (lastSmoothedMagnitude > 0f) {
            0.85f * rawMagnitude + 0.15f * lastSmoothedMagnitude
        } else {
            rawMagnitude
        }
        lastSmoothedMagnitude = smoothedMagnitude

        // 2. Axis-aware energy check:
        // A true chop is directed along the longitudinal axis (y) or screen-normal axis (z).
        // If lateral (x) acceleration dominates (> 55% of total power), it's a side-to-side shake, not a chop.
        val totalSquared = reading.x * reading.x + reading.y * reading.y + reading.z * reading.z
        val isChopAxisAligned = if (totalSquared > 1.0f) {
            (reading.y * reading.y + reading.z * reading.z) / totalSquared >= 0.45f
        } else {
            true
        }

        when (val currentState = _state.value) {
            is GestureState.Idle -> {
                if (smoothedMagnitude >= peakThreshold) {
                    if (isChopAxisAligned) {
                        firstChopTimestampNs = nowNs
                        firstChopPeak = smoothedMagnitude
                        neutralConfirmed = false

                        Log.d(TAG, "First chop peak detected: mag=$smoothedMagnitude, thresh=$peakThreshold, axisAligned=$isChopAxisAligned")

                        diagnosticListener?.onDetectorEvent(
                            detector = "CHOP",
                            eventType = "CANDIDATE_START",
                            timestampNs = nowNs,
                            details = "First chop peak detected: mag=${String.format(Locale.US, "%.2f", smoothedMagnitude)}, thresh=$peakThreshold, axisAligned=true",
                            metrics = mapOf(
                                "peakMagnitude" to smoothedMagnitude,
                                "peakThreshold" to peakThreshold,
                                "axisAligned" to true
                            )
                        )

                        _state.value = GestureState.FirstMovementDetected(
                            timestampNs = nowNs,
                            peakMagnitude = smoothedMagnitude
                        )

                        // Immediately transition to waiting for second chop with deadline
                        _state.value = GestureState.WaitingForSecondMovement(
                            firstTimestampNs = nowNs,
                            deadlineNs = nowNs + maxWindowDurationNs,
                            neutralConfirmed = false
                        )
                    } else {
                        diagnosticListener?.onDetectorEvent(
                            detector = "CHOP",
                            eventType = "PEAK_REJECTED",
                            timestampNs = nowNs,
                            details = "Peak reached ${String.format(Locale.US, "%.2f", smoothedMagnitude)} >= $peakThreshold but rejected (lateral X acceleration dominated)",
                            metrics = mapOf(
                                "peakMagnitude" to smoothedMagnitude,
                                "peakThreshold" to peakThreshold,
                                "axisAligned" to false
                            )
                        )
                    }
                }
            }

            is GestureState.WaitingForSecondMovement -> {
                val elapsedSinceFirstNs = nowNs - firstChopTimestampNs

                // Check window expiration
                if (elapsedSinceFirstNs > maxWindowDurationNs) {
                    Log.d(TAG, "Double chop window expired after ${elapsedSinceFirstNs / 1_000_000}ms without second chop")
                    diagnosticListener?.onDetectorEvent(
                        detector = "CHOP",
                        eventType = "REJECTED",
                        timestampNs = nowNs,
                        details = "Window expired after ${elapsedSinceFirstNs / 1_000_000}ms without second chop (neutralConfirmed=$neutralConfirmed)",
                        metrics = mapOf(
                            "elapsedMs" to elapsedSinceFirstNs / 1_000_000L,
                            "neutralConfirmed" to neutralConfirmed,
                            "reason" to "WINDOW_EXPIRED"
                        )
                    )
                    _events.tryEmit(
                        GestureEvent.Rejected(
                            type = GestureType.DOUBLE_CHOP,
                            reason = "Window expired without second chop",
                            timestampNs = nowNs
                        )
                    )
                    reset()
                    return
                }

                // Check for neutral/recoil interval:
                // During recoil between chops, acceleration must drop into a valley.
                // We define recoil threshold dynamically as max(neutralThreshold * 1.5f, firstChopPeak * 0.5f)
                // to accommodate active arm motion without blocking on static rest.
                val recoilThreshold = max(neutralThreshold * 1.5f, firstChopPeak * 0.5f)
                if (!neutralConfirmed && smoothedMagnitude <= recoilThreshold) {
                    neutralConfirmed = true
                    neutralConfirmedTimestampNs = nowNs
                    Log.d(TAG, "Recoil/valley confirmed: mag=$smoothedMagnitude (recoilThreshold=$recoilThreshold)")
                    diagnosticListener?.onDetectorEvent(
                        detector = "CHOP",
                        eventType = "RECOIL_CONFIRMED",
                        timestampNs = nowNs,
                        details = "Recoil/valley confirmed: mag=${String.format(Locale.US, "%.2f", smoothedMagnitude)} <= recoilThreshold=${String.format(Locale.US, "%.2f", recoilThreshold)}",
                        metrics = mapOf(
                            "smoothedMagnitude" to smoothedMagnitude,
                            "recoilThreshold" to recoilThreshold
                        )
                    )
                    _state.value = currentState.copy(neutralConfirmed = true)
                }

                // Accept second strike only after neutral recoil has been confirmed AND minimum separation elapsed
                if (neutralConfirmed && elapsedSinceFirstNs >= minPeakSeparationNs) {
                    if (smoothedMagnitude >= peakThreshold) {
                        if (isChopAxisAligned) {
                            val durationMs = (elapsedSinceFirstNs / 1_000_000L)
                            val confidence = calculateConfidence(firstChopPeak, smoothedMagnitude)

                            Log.i(
                                TAG,
                                "Double chop recognized! peak1=$firstChopPeak, peak2=$smoothedMagnitude, " +
                                    "duration=${durationMs}ms, confidence=$confidence"
                            )

                            diagnosticListener?.onDetectorEvent(
                                detector = "CHOP",
                                eventType = "RECOGNIZED",
                                timestampNs = nowNs,
                                details = "Double chop recognized! peak1=${String.format(Locale.US, "%.2f", firstChopPeak)}, peak2=${String.format(Locale.US, "%.2f", smoothedMagnitude)}, duration=${durationMs}ms, confidence=${String.format(Locale.US, "%.0f%%", confidence * 100)}",
                                metrics = mapOf(
                                    "peak1" to firstChopPeak,
                                    "peak2" to smoothedMagnitude,
                                    "durationMs" to durationMs,
                                    "confidence" to confidence
                                )
                            )

                            _state.value = GestureState.SecondMovementDetected(
                                timestampNs = nowNs,
                                peakMagnitude = smoothedMagnitude
                            )

                            _events.tryEmit(
                                GestureEvent.Recognized(
                                    type = GestureType.DOUBLE_CHOP,
                                    timestampNs = nowNs,
                                    confidence = confidence,
                                    executionDurationMs = durationMs
                                )
                            )

                            // Enter cooldown to avoid duplicate triggers from deceleration rebound
                            cooldownUntilNs = nowNs + cooldownDurationNs
                            _state.value = GestureState.Cooldown(untilNs = cooldownUntilNs)
                        } else {
                            diagnosticListener?.onDetectorEvent(
                                detector = "CHOP",
                                eventType = "SECOND_PEAK_REJECTED",
                                timestampNs = nowNs,
                                details = "Second peak reached ${String.format(Locale.US, "%.2f", smoothedMagnitude)} >= $peakThreshold but rejected (lateral X acceleration dominated)",
                                metrics = mapOf(
                                    "peakMagnitude" to smoothedMagnitude,
                                    "axisAligned" to false
                                )
                            )
                        }
                    }
                }
            }

            is GestureState.Cooldown -> {
                if (nowNs >= currentState.untilNs) {
                    Log.d(TAG, "Chop cooldown completed, returning to Idle")
                    reset()
                }
            }

            is GestureState.FirstMovementDetected,
            is GestureState.SecondMovementDetected -> {
                // Transient states handled above
            }
        }
    }

    private fun calculateConfidence(peak1: Float, peak2: Float): Float {
        val ratio = if (peak1 > 0f && peak2 > 0f) {
            val minP = min(peak1, peak2)
            val maxP = max(peak1, peak2)
            minP / maxP
        } else 0.5f

        val intensityBonus = ((peak1 + peak2) / (2f * peakThreshold)).coerceIn(0.8f, 1.2f)
        return (ratio * 0.7f + intensityBonus * 0.3f).coerceIn(0.1f, 1.0f)
    }

    override fun reset() {
        _state.value = GestureState.Idle
        firstChopTimestampNs = 0L
        firstChopPeak = 0f
        neutralConfirmed = false
        neutralConfirmedTimestampNs = 0L
        cooldownUntilNs = 0L
        lastSmoothedMagnitude = 0f
    }
}
