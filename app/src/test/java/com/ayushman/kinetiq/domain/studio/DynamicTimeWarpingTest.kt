package com.ayushman.kinetiq.domain.studio

import com.ayushman.kinetiq.domain.studio.engine.DynamicTimeWarping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicTimeWarpingTest {

    @Test
    fun testIdenticalTrajectories_yieldsZeroDistance() {
        val candidate = StudioTestHelper.createCandidate(sampleCount = 25, peakAccel = 18.0)
        val distance = DynamicTimeWarping.computeDistance(candidate, candidate)
        assertEquals(0.0, distance, 1e-4)
    }

    @Test
    fun testSimilarTrajectories_yieldsSmallDistance() {
        val template = StudioTestHelper.createCandidate(sampleCount = 25, peakAccel = 18.0)
        // Slightly perturbed candidate (20 samples instead of 25, slight amplitude difference)
        val candidate = StudioTestHelper.createCandidate(sampleCount = 20, peakAccel = 17.5)

        val distance = DynamicTimeWarping.computeDistance(candidate, template)
        assertTrue("Distance should be small for similar trajectories, but was $distance", distance < 1.0)
    }

    @Test
    fun testDifferentTrajectories_yieldsLargeDistance() {
        val template = StudioTestHelper.createCandidate(sampleCount = 25, peakAccel = 20.0, peakGyro = 6.0)
        val noise = StudioTestHelper.createNoiseCandidate(sampleCount = 20)

        val distance = DynamicTimeWarping.computeDistance(noise, template)
        assertTrue("Distance between noise and gesture should be large, but was $distance", distance > 2.5)
    }

    @Test
    fun testSubMinimumSampleCount_returnsInfinity() {
        val invalidCandidate = StudioTestHelper.createCandidate(sampleCount = 1)
        val validTemplate = StudioTestHelper.createCandidate(sampleCount = 20)

        val distance = DynamicTimeWarping.computeDistance(invalidCandidate, validTemplate)
        assertEquals(Double.POSITIVE_INFINITY, distance, 0.0)
    }

    @Test
    fun testKinematicPenaltyContribution() {
        val c1 = StudioTestHelper.createCandidate(sampleCount = 20, peakAccel = 10.0, durationMs = 500L)
        val c2 = StudioTestHelper.createCandidate(sampleCount = 20, peakAccel = 25.0, durationMs = 800L)

        val d1 = DynamicTimeWarping.computeDistance(c1, c1)
        val d2 = DynamicTimeWarping.computeDistance(c1, c2)

        assertTrue(d1 < d2)
    }
}

