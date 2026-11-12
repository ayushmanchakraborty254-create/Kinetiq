package com.ayushman.kinetiq.domain.studio

import com.ayushman.kinetiq.domain.studio.engine.PersonalizedGestureMatcher
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import com.ayushman.kinetiq.domain.studio.model.KinematicBaselines
import com.ayushman.kinetiq.domain.studio.model.ValidationStatus
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PersonalizedGestureMatcherTest {

    private lateinit var matcher: PersonalizedGestureMatcher
    private lateinit var validTemplate: GestureTemplate

    @Before
    fun setUp() {
        matcher = PersonalizedGestureMatcher()
        val medoid = StudioTestHelper.createCandidate(sampleCount = 20, peakAccel = 15.0, strokeCount = 2)
        val negatives = listOf(
            StudioTestHelper.createNoiseCandidate(sampleCount = 15, durationMs = 300L),
            StudioTestHelper.createNoiseCandidate(sampleCount = 20, durationMs = 500L)
        )

        validTemplate = GestureTemplate(
            id = "test_gesture",
            displayName = "Test Double Chop",
            isCorePersonalization = false,
            requiredStrokeCount = 2,
            assignedAction = ActionDefinition.ToggleFlashlight(),
            isEnabled = true,
            validationStatus = ValidationStatus.PROVISIONALLY_VALIDATED,
            createdEpochMs = System.currentTimeMillis(),
            positiveDistanceThreshold = 5.0,
            positiveClusterMean = 1.0,
            positiveClusterStdDev = 0.5,
            negativeMarginRatio = 0.85,
            slantedBandRadius = 8,
            kinematicWeight = 0.20,
            kinematicBaselines = KinematicBaselines(
                peakAccelMean = 15.0,
                peakAccelStdDev = 1.0,
                peakGyroMean = 3.0,
                peakGyroStdDev = 0.5,
                durationMeanMs = 600.0,
                durationStdDevMs = 50.0,
                pauseMeanMs = 100.0,
                pauseStdDevMs = 20.0
            ),
            medoidTemplate = medoid,
            userNegativeTemplates = negatives
        )
    }

    @Test
    fun testStructuralCheck_rejectsDifferentStrokeCount() {
        val singleStrokeCandidate = StudioTestHelper.createCandidate(strokeCount = 1, sampleCount = 20)
        val result = matcher.evaluate(singleStrokeCandidate, validTemplate)

        assertTrue(result is PersonalizedGestureMatcher.MatchResult.Rejected)
        assertTrue((result as PersonalizedGestureMatcher.MatchResult.Rejected).reason.contains("Structural mismatch"))
    }

    @Test
    fun testMandatoryUserNegativeGate_rejectsIfEmpty() {
        val templateWithoutNegatives = validTemplate.copy(userNegativeTemplates = emptyList())
        val candidate = StudioTestHelper.createCandidate(sampleCount = 20, strokeCount = 2)
        val result = matcher.evaluate(candidate, templateWithoutNegatives)

        assertTrue(result is PersonalizedGestureMatcher.MatchResult.Rejected)
        assertTrue((result as PersonalizedGestureMatcher.MatchResult.Rejected).reason.contains("User negative library is empty"))
    }

    @Test
    fun testPocketProtectionGate_suppressesWhenCovered() {
        val candidate = StudioTestHelper.createCandidate(sampleCount = 20, strokeCount = 2)
        val result = matcher.evaluate(
            candidate = candidate,
            template = validTemplate,
            isPocketProtectionEnabled = true,
            isPocketCovered = true
        )

        assertTrue(result is PersonalizedGestureMatcher.MatchResult.Rejected)
        assertTrue((result as PersonalizedGestureMatcher.MatchResult.Rejected).reason.contains("pocket protection"))
    }

    @Test
    fun testValidCandidate_matchesSuccessfully() {
        // Candidate identical to medoid
        val candidate = StudioTestHelper.createCandidate(sampleCount = 20, peakAccel = 15.0, strokeCount = 2)
        val result = matcher.evaluate(candidate, validTemplate)

        assertTrue(result is PersonalizedGestureMatcher.MatchResult.Matched)
        val matched = result as PersonalizedGestureMatcher.MatchResult.Matched
        assertEquals("test_gesture", matched.gestureId)
        assertTrue(matched.confidencePercentage > 70)
    }

    private fun assertEquals(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}

