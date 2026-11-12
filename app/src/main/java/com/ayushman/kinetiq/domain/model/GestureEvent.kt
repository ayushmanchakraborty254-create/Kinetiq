package com.ayushman.kinetiq.domain.model

sealed class GestureEvent {
    data class Recognized(
        val type: GestureType,
        val timestampNs: Long,
        val confidence: Float,
        val executionDurationMs: Long,
        val isPersonalized: Boolean = false
    ) : GestureEvent()

    data class Rejected(
        val type: GestureType,
        val reason: String,
        val timestampNs: Long
    ) : GestureEvent()
}

