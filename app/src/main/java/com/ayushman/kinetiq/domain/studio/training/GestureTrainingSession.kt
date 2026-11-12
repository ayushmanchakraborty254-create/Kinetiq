package com.ayushman.kinetiq.domain.studio.training

import android.util.Log
import com.ayushman.kinetiq.domain.studio.action.ActionSuppressionManager
import com.ayushman.kinetiq.domain.studio.action.ActionSuppressionSession
import com.ayushman.kinetiq.domain.studio.action.TrainingSessionResult
import com.ayushman.kinetiq.domain.studio.engine.CalibrationEngine
import com.ayushman.kinetiq.domain.studio.engine.DynamicTimeWarping
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import com.ayushman.kinetiq.domain.studio.model.TrainingStep
import com.ayushman.kinetiq.domain.studio.model.ValidationStatus
import com.ayushman.kinetiq.domain.studio.storage.GestureTemplateStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * State coordinator for the 3-Tier Guided Training Workflow.
 * Manages exclusive session acquisition, step transitions, validation gates, and profile generation.
 */
class GestureTrainingSession(
    val gestureId: String,
    val displayName: String,
    val isCorePersonalization: Boolean,
    val requiredStrokeCount: Int,
    val assignedAction: ActionDefinition,
    private val suppressionManager: ActionSuppressionManager,
    private val templateStorage: GestureTemplateStorage
) {
    companion object {
        private const val TAG = "KinetiqTrainingSession"
    }

    val sessionId: String = UUID.randomUUID().toString()
    private var activeSession: ActionSuppressionSession? = null

    private val _currentStep = MutableStateFlow<TrainingStep>(TrainingStep.Idle)
    val currentStep: StateFlow<TrainingStep> = _currentStep.asStateFlow()

    // Sample buffers
    val tier1TrainingSamples = mutableListOf<MotionCandidate>()
    val tier2ConfirmationSamples = mutableListOf<MotionCandidate>()
    val tier3UserNegatives = mutableListOf<MotionCandidate>()
    val tier4HoldOutPositives = mutableListOf<MotionCandidate>()
    val tier4HoldOutNegatives = mutableListOf<MotionCandidate>()

    // Calibration cache
    private var calibrationResult: CalibrationEngine.CalibrationResult? = null

    /**
     * Starts the training session by acquiring exclusive action suppression.
     */
    suspend fun start(): Boolean {
        return when (val result = suppressionManager.acquireExclusiveSession(sessionId)) {
            is TrainingSessionResult.Acquired -> {
                activeSession = result.session
                tier1TrainingSamples.clear()
                tier2ConfirmationSamples.clear()
                tier3UserNegatives.clear()
                tier4HoldOutPositives.clear()
                tier4HoldOutNegatives.clear()
                calibrationResult = null

                _currentStep.value = TrainingStep.Tier1Training(currentRepetition = 0, targetRepetitions = 8)
                Log.i(TAG, "Exclusive training session acquired successfully: $sessionId")
                true
            }

            is TrainingSessionResult.Rejected -> {
                Log.w(TAG, "Training session acquisition rejected: ${result.reason}")
                _currentStep.value = TrainingStep.Failed(result.reason)
                false
            }
        }
    }

    /**
     * Submits a recorded motion candidate to the active training step.
     */
    fun submitCandidate(candidate: MotionCandidate) {
        val step = _currentStep.value

        when (step) {
            is TrainingStep.Tier1Training -> {
                tier1TrainingSamples.add(candidate)
                val count = tier1TrainingSamples.size
                if (count < 8) {
                    _currentStep.value = TrainingStep.Tier1Training(currentRepetition = count, targetRepetitions = 8)
                } else {
                    // Calibrate positive cluster strictly on the 8 training repetitions
                    when (val outcome = CalibrationEngine.calibrateTrainingSet(tier1TrainingSamples)) {
                        is CalibrationEngine.CalibrationOutcome.Success -> {
                            calibrationResult = outcome.result
                            _currentStep.value = TrainingStep.Tier2Confirmation(currentRepetition = 0, targetRepetitions = 2)
                        }

                        is CalibrationEngine.CalibrationOutcome.InconsistentRepetitions -> {
                            tier1TrainingSamples.clear()
                            _currentStep.value = TrainingStep.Failed(outcome.message)
                        }
                    }
                }
            }

            is TrainingStep.Tier2Confirmation -> {
                tier2ConfirmationSamples.add(candidate)
                val count = tier2ConfirmationSamples.size
                if (count < 2) {
                    _currentStep.value = TrainingStep.Tier2Confirmation(currentRepetition = count, targetRepetitions = 2)
                } else {
                    // Move to Tier 3 User Negative capture
                    _currentStep.value = TrainingStep.Tier3NegativeCapture.DeskPickup(currentRepetition = 0, targetRepetitions = 3)
                }
            }

            is TrainingStep.Tier3NegativeCapture.DeskPickup -> {
                tier3UserNegatives.add(candidate)
                val count = tier3UserNegatives.count { it.strokeCount >= 1 }
                if (count < 3) {
                    _currentStep.value = TrainingStep.Tier3NegativeCapture.DeskPickup(currentRepetition = count, targetRepetitions = 3)
                } else {
                    _currentStep.value = TrainingStep.Tier3NegativeCapture.DeskPlacement(currentRepetition = 0, targetRepetitions = 2)
                }
            }

            is TrainingStep.Tier3NegativeCapture.DeskPlacement -> {
                tier3UserNegatives.add(candidate)
                val count = tier3UserNegatives.size - 3 // placements
                if (count < 2) {
                    _currentStep.value = TrainingStep.Tier3NegativeCapture.DeskPlacement(currentRepetition = count, targetRepetitions = 2)
                } else {
                    _currentStep.value = TrainingStep.Tier3NegativeCapture.Walking(remainingSeconds = 5)
                }
            }

            is TrainingStep.Tier3NegativeCapture.Walking,
            is TrainingStep.Tier3NegativeCapture.CasualHandling -> {
                tier3UserNegatives.add(candidate)
            }

            is TrainingStep.Tier4HoldOutValidation.Positives -> {
                tier4HoldOutPositives.add(candidate)
                val count = tier4HoldOutPositives.size
                if (count < 5) {
                    _currentStep.value = TrainingStep.Tier4HoldOutValidation.Positives(currentRepetition = count, targetRepetitions = 5)
                } else {
                    _currentStep.value = TrainingStep.Tier4HoldOutValidation.Negatives(
                        currentMovement = "Desk Pickup",
                        currentRepetition = 0,
                        totalRepetitions = 10
                    )
                }
            }

            is TrainingStep.Tier4HoldOutValidation.Negatives -> {
                tier4HoldOutNegatives.add(candidate)
                val count = tier4HoldOutNegatives.size
                if (count < 10) {
                    val movementName = when (count) {
                        in 0..2 -> "Desk Pickup"
                        in 3..4 -> "Desk Placement"
                        in 5..7 -> "Walking swing"
                        else -> "Casual handling"
                    }
                    _currentStep.value = TrainingStep.Tier4HoldOutValidation.Negatives(
                        currentMovement = movementName,
                        currentRepetition = count,
                        totalRepetitions = 10
                    )
                } else {
                    evaluateHoldOutValidation()
                }
            }

            else -> {
                Log.d(TAG, "Candidate received during non-recording step: $step")
            }
        }
    }

    fun completeContinuousNegative(isWalking: Boolean) {
        if (isWalking) {
            _currentStep.value = TrainingStep.Tier3NegativeCapture.CasualHandling(remainingSeconds = 5)
        } else {
            // Tier 3 complete -> Move to Tier 4 Hold-Out Validation
            _currentStep.value = TrainingStep.Tier4HoldOutValidation.Positives(currentRepetition = 0, targetRepetitions = 5)
        }
    }

    private fun evaluateHoldOutValidation() {
        val cal = calibrationResult
        if (cal == null) {
            _currentStep.value = TrainingStep.Failed("Calibration result missing")
            return
        }

        // 1. Evaluate 5 fresh positives
        for ((idx, posSample) in tier4HoldOutPositives.withIndex()) {
            val dPos = DynamicTimeWarping.computeDistance(
                candidate = posSample,
                template = cal.medoid,
                stdDevAccel = cal.kinematicBaselines.peakAccelStdDev,
                stdDevGyro = cal.kinematicBaselines.peakGyroStdDev,
                stdDevDuration = cal.kinematicBaselines.durationStdDevMs,
                stdDevPause = cal.kinematicBaselines.pauseStdDevMs ?: 30.0
            )

            var minNegDist = Double.POSITIVE_INFINITY
            for (neg in tier3UserNegatives) {
                val dNeg = DynamicTimeWarping.computeDistance(posSample, neg)
                if (dNeg < minNegDist) minNegDist = dNeg
            }

            if (dPos > cal.thresholdPos || dPos >= minNegDist * 0.85) {
                _currentStep.value = TrainingStep.Failed(
                    "Hold-Out Validation Failed on Positive #$idx (D_pos ${String.format("%.2f", dPos)} > Threshold). Please retrain."
                )
                return
            }
        }

        // 2. Evaluate 10 fresh negatives (must NOT trigger false positive)
        for ((idx, negSample) in tier4HoldOutNegatives.withIndex()) {
            val dPos = DynamicTimeWarping.computeDistance(
                candidate = negSample,
                template = cal.medoid,
                stdDevAccel = cal.kinematicBaselines.peakAccelStdDev,
                stdDevGyro = cal.kinematicBaselines.peakGyroStdDev,
                stdDevDuration = cal.kinematicBaselines.durationStdDevMs,
                stdDevPause = cal.kinematicBaselines.pauseStdDevMs ?: 30.0
            )

            var minNegDist = Double.POSITIVE_INFINITY
            for (neg in tier3UserNegatives) {
                val dNeg = DynamicTimeWarping.computeDistance(negSample, neg)
                if (dNeg < minNegDist) minNegDist = dNeg
            }

            if (negSample.strokeCount == requiredStrokeCount && dPos <= cal.thresholdPos && dPos < minNegDist * 0.85) {
                _currentStep.value = TrainingStep.Failed(
                    "Hold-Out Validation Failed: Negative movement #$idx triggered a false match. Please retrain."
                )
                return
            }
        }

        // 3. Conflict Advisory Check against existing gesture profiles
        var advisoryWarning: String? = null
        val existingTemplates = templateStorage.loadAllTemplates()
        for (existing in existingTemplates) {
            if (existing.id != gestureId) {
                val warning = CalibrationEngine.checkConflictAdvisory(
                    medoidA = cal.medoid,
                    stdDevA = cal.clusterStdDev,
                    gestureAName = displayName,
                    medoidB = existing.medoidTemplate,
                    stdDevB = existing.positiveClusterStdDev,
                    gestureBName = existing.displayName
                )
                if (warning != null) {
                    advisoryWarning = warning
                    break
                }
            }
        }

        _currentStep.value = TrainingStep.Tier5ConflictAdvisory(advisoryWarning)
    }

    /**
     * Builds and persists the final validated GestureTemplate profile.
     */
    fun saveGestureProfile(): Boolean {
        val cal = calibrationResult ?: return false

        val template = GestureTemplate(
            id = gestureId,
            displayName = displayName,
            isCorePersonalization = isCorePersonalization,
            requiredStrokeCount = requiredStrokeCount,
            assignedAction = assignedAction,
            isEnabled = true,
            validationStatus = ValidationStatus.PROVISIONALLY_VALIDATED,
            createdEpochMs = System.currentTimeMillis(),
            positiveDistanceThreshold = cal.thresholdPos,
            positiveClusterMean = cal.clusterMean,
            positiveClusterStdDev = cal.clusterStdDev,
            negativeMarginRatio = 0.85,
            slantedBandRadius = 8,
            kinematicWeight = 0.20,
            kinematicBaselines = cal.kinematicBaselines,
            medoidTemplate = cal.medoid,
            userNegativeTemplates = ArrayList(tier3UserNegatives)
        )

        val saved = templateStorage.saveTemplate(template)
        if (saved) {
            _currentStep.value = TrainingStep.Completed(ValidationStatus.PROVISIONALLY_VALIDATED)
        }
        teardown()
        return saved
    }

    /**
     * Safely aborts and cleans up the training session, releasing action suppression.
     */
    fun abort() {
        teardown()
        _currentStep.value = TrainingStep.Idle
    }

    private fun teardown() {
        // Detach and clear buffers BEFORE closing suppression session
        tier1TrainingSamples.clear()
        tier2ConfirmationSamples.clear()
        tier3UserNegatives.clear()
        tier4HoldOutPositives.clear()
        tier4HoldOutNegatives.clear()

        activeSession?.close()
        activeSession = null
        Log.i(TAG, "Training session cleaned up and suppression released: $sessionId")
    }
}

