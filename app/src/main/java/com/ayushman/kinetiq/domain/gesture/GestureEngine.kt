package com.ayushman.kinetiq.domain.gesture

import android.util.Log
import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureState
import com.ayushman.kinetiq.domain.model.GestureType
import com.ayushman.kinetiq.domain.model.SensorReading
import com.ayushman.kinetiq.domain.model.UserPreferences
import com.ayushman.kinetiq.domain.studio.action.ActionExecutor
import com.ayushman.kinetiq.domain.studio.action.ActionSuppressionManager
import com.ayushman.kinetiq.domain.studio.engine.MotionSegmenter
import com.ayushman.kinetiq.domain.studio.engine.PersonalizedGestureMatcher
import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import com.ayushman.kinetiq.domain.studio.storage.GestureTemplateStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class GestureEngine(
    private val scope: CoroutineScope,
    private val chopDetector: ChopGestureDetector = ChopGestureDetector(),
    private val twistDetector: TwistGestureDetector = TwistGestureDetector(),
    private val cooldownManager: GestureCooldownManager = GestureCooldownManager(),
    private val templateStorage: GestureTemplateStorage? = null,
    private val actionExecutor: ActionExecutor? = null,
    private val suppressionManager: ActionSuppressionManager? = null
) {
    companion object {
        private const val TAG = "KinetiqEngine"

        /**
         * Diagnostic override: when true, recognized gestures are logged
         * but NOT dispatched to actions (flashlight or camera remain untouched).
         */
        @Volatile
        var isDiagnosticSuppressed: Boolean = false
    }

    private val segmenter = MotionSegmenter(scope = scope)
    private val personalizedMatcher = PersonalizedGestureMatcher()

    private val _gestureEvents = MutableSharedFlow<GestureEvent.Recognized>(extraBufferCapacity = 8)
    val gestureEvents: SharedFlow<GestureEvent.Recognized> = _gestureEvents.asSharedFlow()

    @Volatile
    private var isPocketCovered: Boolean = false

    @Volatile
    private var isMonitoringActive: Boolean = false

    @Volatile
    private var isChopActive: Boolean = true

    @Volatile
    private var isTwistActive: Boolean = true

    @Volatile
    private var isPocketProtectionActive: Boolean = true

    init {
        scope.launch {
            chopDetector.events.collect { event ->
                if (event is GestureEvent.Recognized) {
                    handleRecognizedEvent(event)
                }
            }
        }
        scope.launch {
            twistDetector.events.collect { event ->
                if (event is GestureEvent.Recognized) {
                    handleRecognizedEvent(event)
                }
            }
        }
        scope.launch {
            segmenter.candidateEvents.collect { candidate ->
                handleCandidateMotion(candidate)
            }
        }
    }

    fun updatePreferences(preferences: UserPreferences) {
        isMonitoringActive = preferences.isMonitoringEnabled
        isChopActive = preferences.isChopEnabled
        isTwistActive = preferences.isTwistEnabled
        isPocketProtectionActive = preferences.isPocketProtectionEnabled

        chopDetector.sensitivity = preferences.chopSensitivity
        chopDetector.customPeakThresholdOverride = preferences.customChopPeakThreshold

        twistDetector.sensitivity = preferences.twistSensitivity
        twistDetector.customVelocityThresholdOverride = preferences.customTwistPeakThreshold

        if (!isMonitoringActive) {
            reset()
        }
    }

    fun onLinearAccelerationReading(reading: SensorReading.LinearAcceleration) {
        if (!isMonitoringActive) return
        if (isPocketProtectionActive && isPocketCovered) return

        // Feed to segmenter for personalized and custom gesture detection
        segmenter.feedLinearAcceleration(
            reading.x.toDouble(),
            reading.y.toDouble(),
            reading.z.toDouble(),
            reading.timestampNs
        )

        // Slot exclusivity: Only process with factory detector if no personalized template is active for chop
        val hasPersonalizedChop = templateStorage?.loadTemplate(GestureTemplate.CORE_CHOP_ID)?.isEnabled == true
        if (!hasPersonalizedChop && isChopActive) {
            chopDetector.processReading(reading)
        }
    }

    fun onGyroscopeReading(reading: SensorReading.Gyroscope) {
        if (!isMonitoringActive) return
        if (isPocketProtectionActive && isPocketCovered) return

        // Feed to segmenter for personalized and custom gesture detection
        segmenter.feedGyroscope(
            reading.x.toDouble(),
            reading.y.toDouble(),
            reading.z.toDouble(),
            reading.timestampNs
        )

        // Slot exclusivity: Only process with factory detector if no personalized template is active for twist
        val hasPersonalizedTwist = templateStorage?.loadTemplate(GestureTemplate.CORE_TWIST_ID)?.isEnabled == true
        if (!hasPersonalizedTwist && isTwistActive) {
            twistDetector.processReading(reading)
        }
    }

    fun onProximityReading(reading: SensorReading.Proximity) {
        isPocketCovered = reading.isNear
        if (isPocketProtectionActive && isPocketCovered) {
            chopDetector.reset()
            twistDetector.reset()
            segmenter.reset()
        }
    }

    private fun handleCandidateMotion(candidate: MotionCandidate) {
        if (!isMonitoringActive) return
        if (isPocketProtectionActive && isPocketCovered) return
        if (isDiagnosticSuppressed) return
        if (suppressionManager?.isActionSuppressed == true) return

        val templates = templateStorage?.loadAllTemplates()?.filter { it.isEnabled } ?: return
        if (templates.isEmpty()) return

        when (val result = personalizedMatcher.evaluateBestMatch(candidate, templates, isPocketProtectionActive, isPocketCovered)) {
            is PersonalizedGestureMatcher.MatchResult.Matched -> {
                val matchedTemplate = templates.find { it.id == result.gestureId } ?: return

                // Slot gating
                if (matchedTemplate.id == GestureTemplate.CORE_CHOP_ID && !isChopActive) return
                if (matchedTemplate.id == GestureTemplate.CORE_TWIST_ID && !isTwistActive) return

                Log.i(TAG, "Personalized gesture matched: ${matchedTemplate.displayName} (id=${matchedTemplate.id})")
                actionExecutor?.executeAction(candidate, matchedTemplate.assignedAction)

                val gestureType = when (matchedTemplate.id) {
                    GestureTemplate.CORE_CHOP_ID -> GestureType.DOUBLE_CHOP
                    GestureTemplate.CORE_TWIST_ID -> GestureType.DOUBLE_TWIST
                    else -> null
                }
                gestureType?.let { type ->
                    _gestureEvents.tryEmit(
                        GestureEvent.Recognized(
                            type = type,
                            timestampNs = candidate.physicalEndTimestampMs * 1_000_000L,
                            confidence = result.confidencePercentage / 100f,
                            executionDurationMs = candidate.durationMs,
                            isPersonalized = true
                        )
                    )
                }
            }
            is PersonalizedGestureMatcher.MatchResult.Rejected -> {
                Log.d(TAG, "Candidate rejected: ${result.reason}")
            }
        }
    }

    private fun handleRecognizedEvent(event: GestureEvent.Recognized) {
        if (isDiagnosticSuppressed) {
            Log.d(TAG, "Suppressed ${event.type} action dispatch because diagnostic mode is active")
            return
        }
        if (!isMonitoringActive) {
            Log.d(TAG, "Ignored ${event.type} because monitoring is disabled")
            return
        }
        if (isPocketProtectionActive && isPocketCovered) {
            Log.d(TAG, "Ignored ${event.type} because device is in pocket")
            return
        }

        // Verify gesture type is allowed
        when (event.type) {
            GestureType.DOUBLE_CHOP -> if (!isChopActive) return
            GestureType.DOUBLE_TWIST -> if (!isTwistActive) return
        }

        // Check global and per-gesture cooldown
        if (!cooldownManager.canTrigger(event.type)) {
            Log.d(TAG, "Suppressed ${event.type} due to active cooldown lockout")
            return
        }

        // Check action suppression barrier (e.g. during studio training)
        if (suppressionManager?.isActionSuppressed == true) {
            Log.d(TAG, "Suppressed ${event.type} due to active training session suppression")
            return
        }

        // Record trigger to establish cross-gesture lockout
        cooldownManager.recordTrigger(event.type)

        // Reset the counterpart detector immediately to eliminate any lingering state or conflict
        when (event.type) {
            GestureType.DOUBLE_CHOP -> {
                twistDetector.reset()
                Log.i(TAG, ">>> DOUBLE CHOP recognized -> Dispatching flashlight toggle")
            }
            GestureType.DOUBLE_TWIST -> {
                chopDetector.reset()
                Log.i(TAG, ">>> DOUBLE TWIST recognized -> Dispatching camera launch")
            }
        }

        _gestureEvents.tryEmit(event)
    }

    fun getChopDetector(): ChopGestureDetector = chopDetector
    fun getTwistDetector(): TwistGestureDetector = twistDetector

    fun reset() {
        chopDetector.reset()
        twistDetector.reset()
        cooldownManager.reset()
        segmenter.reset()
    }
}
