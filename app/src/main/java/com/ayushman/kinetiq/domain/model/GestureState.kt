package com.ayushman.kinetiq.domain.model

sealed class GestureState {
    object Idle : GestureState() {
        override fun toString(): String = "Idle"
    }

    data class FirstMovementDetected(
        val timestampNs: Long,
        val peakMagnitude: Float
    ) : GestureState()

    data class WaitingForSecondMovement(
        val firstTimestampNs: Long,
        val deadlineNs: Long,
        val neutralConfirmed: Boolean = false
    ) : GestureState()

    data class SecondMovementDetected(
        val timestampNs: Long,
        val peakMagnitude: Float
    ) : GestureState()

    data class Cooldown(
        val untilNs: Long
    ) : GestureState()
}

