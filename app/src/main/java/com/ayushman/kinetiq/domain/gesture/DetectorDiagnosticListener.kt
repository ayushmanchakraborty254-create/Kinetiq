package com.ayushman.kinetiq.domain.gesture

/**
 * Diagnostic callback for recording detector internal state transitions,
 * candidate starts, direction reversals, peak evaluations, rejection reasons,
 * and final recognition decisions without affecting detector state or thresholds.
 */
interface DetectorDiagnosticListener {
    fun onDetectorEvent(
        detector: String,
        eventType: String,
        timestampNs: Long,
        details: String,
        metrics: Map<String, Any> = emptyMap()
    )
}

