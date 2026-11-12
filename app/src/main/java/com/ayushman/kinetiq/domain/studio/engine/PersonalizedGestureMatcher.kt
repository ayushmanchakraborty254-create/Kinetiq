package com.ayushman.kinetiq.domain.studio.engine

import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-template personalized gesture matcher.
 * Evaluates candidate segments against trained gesture profiles.
 */
class PersonalizedGestureMatcher {

    sealed class MatchResult {
        data class Matched(
            val gestureId: String,
            val distance: Double,
            val positiveThreshold: Double,
            val minNegativeDistance: Double,
            val confidencePercentage: Int
        ) : MatchResult()

        data class Rejected(val reason: String) : MatchResult()
    }

    /**
     * Evaluates candidate C against a trained template T according to the 5-point decision rule.
     *
     * @param candidate Segmented motion candidate.
     * @param template Trained gesture profile.
     * @param isPocketProtectionEnabled User preference setting.
     * @param isPocketCovered Current hardware proximity state (true = NEAR/covered).
     */
    fun evaluate(
        candidate: MotionCandidate,
        template: GestureTemplate,
        isPocketProtectionEnabled: Boolean = false,
        isPocketCovered: Boolean = false
    ): MatchResult {
        if (!template.isEnabled) {
            return MatchResult.Rejected("Template '${template.displayName}' is disabled")
        }

        // 1. Structural Prerequisite: stroke count must match
        if (candidate.strokeCount != template.requiredStrokeCount) {
            return MatchResult.Rejected(
                "Structural mismatch: Candidate strokeCount (${candidate.strokeCount}) != required (${template.requiredStrokeCount})"
            )
        }

        // 2. Mandatory User Negative Library Gate
        if (template.userNegativeTemplates.isEmpty()) {
            return MatchResult.Rejected(
                "Gated off: User negative library is empty. Negative captures are mandatory before enabling recognition."
            )
        }

        // 3. Optional Pocket Protection Gate
        if (isPocketProtectionEnabled && isPocketCovered) {
            return MatchResult.Rejected("Suppressed by pocket protection (proximity near)")
        }

        // 4. Compute Positive Distance to Medoid
        val kb = template.kinematicBaselines
        val dPos = DynamicTimeWarping.computeDistance(
            candidate = candidate,
            template = template.medoidTemplate,
            stdDevAccel = kb.peakAccelStdDev,
            stdDevGyro = kb.peakGyroStdDev,
            stdDevDuration = kb.durationStdDevMs,
            stdDevPause = kb.pauseStdDevMs ?: 30.0
        )

        // Absolute Distance Ceiling
        if (dPos > template.positiveDistanceThreshold || dPos.isInfinite()) {
            return MatchResult.Rejected(
                "Distance ceiling exceeded: D_pos (${String.format("%.2f", dPos)}) > T_pos (${String.format("%.2f", template.positiveDistanceThreshold)})"
            )
        }

        // 5. Negative Margin Ratio Check
        var minNegDist = Double.POSITIVE_INFINITY
        for (neg in template.userNegativeTemplates) {
            val dNeg = DynamicTimeWarping.computeDistance(candidate, neg)
            if (dNeg < minNegDist) minNegDist = dNeg
        }

        val requiredMargin = minNegDist * template.negativeMarginRatio
        if (dPos >= requiredMargin) {
            return MatchResult.Rejected(
                "Negative margin failed: D_pos (${String.format("%.2f", dPos)}) >= min(D_neg) * gamma (${String.format("%.2f", requiredMargin)})"
            )
        }

        // Derived UI Confidence Score
        val confidence = max(0, min(100, ((1.0 - (dPos / template.positiveDistanceThreshold)) * 100).toInt()))

        return MatchResult.Matched(
            gestureId = template.id,
            distance = dPos,
            positiveThreshold = template.positiveDistanceThreshold,
            minNegativeDistance = minNegDist,
            confidencePercentage = confidence
        )
    }

    /**
     * Evaluates a candidate across a list of enabled templates and returns the best match (lowest distance).
     */
    fun evaluateBestMatch(
        candidate: MotionCandidate,
        templates: List<GestureTemplate>,
        isPocketProtectionEnabled: Boolean = false,
        isPocketCovered: Boolean = false
    ): MatchResult {
        var bestMatch: MatchResult.Matched? = null
        for (template in templates) {
            val result = evaluate(candidate, template, isPocketProtectionEnabled, isPocketCovered)
            if (result is MatchResult.Matched) {
                if (bestMatch == null || result.distance < bestMatch.distance) {
                    bestMatch = result
                }
            }
        }
        return bestMatch ?: MatchResult.Rejected("No matching gesture profile found")
    }
}
