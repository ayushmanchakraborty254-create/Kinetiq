package com.ayushman.kinetiq.domain.studio.model

/**
 * State machine states for the 3-tier guided gesture training workflow.
 */
sealed class TrainingStep {
    data object Idle : TrainingStep()

    data class Tier1Training(val currentRepetition: Int, val targetRepetitions: Int = 8) : TrainingStep()

    data class Tier2Confirmation(val currentRepetition: Int, val targetRepetitions: Int = 2) : TrainingStep()

    sealed class Tier3NegativeCapture : TrainingStep() {
        data class DeskPickup(val currentRepetition: Int, val targetRepetitions: Int = 3) : Tier3NegativeCapture()
        data class DeskPlacement(val currentRepetition: Int, val targetRepetitions: Int = 2) : Tier3NegativeCapture()
        data class Walking(val remainingSeconds: Int = 5) : Tier3NegativeCapture()
        data class CasualHandling(val remainingSeconds: Int = 5) : Tier3NegativeCapture()
    }

    sealed class Tier4HoldOutValidation : TrainingStep() {
        data class Positives(val currentRepetition: Int, val targetRepetitions: Int = 5) : Tier4HoldOutValidation()
        data class Negatives(val currentMovement: String, val currentRepetition: Int, val totalRepetitions: Int = 10) : Tier4HoldOutValidation()
    }

    data class Tier5ConflictAdvisory(val advisoryWarning: String?) : TrainingStep()

    data class Completed(val status: ValidationStatus) : TrainingStep()

    data class Failed(val reason: String) : TrainingStep()
}

enum class ValidationStatus {
    NOT_VALIDATED,
    PROVISIONALLY_VALIDATED,
    VALIDATION_FAILED
}

