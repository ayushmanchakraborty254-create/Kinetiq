package com.ayushman.kinetiq.domain.studio

import com.ayushman.kinetiq.domain.studio.engine.CalibrationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationEngineTest {

    @Test(expected = IllegalArgumentException::class)
    fun testCalibrateTrainingSet_requiresEightSamples() {
        val samples = (1..7).map { StudioTestHelper.createCandidate() }
        CalibrationEngine.calibrateTrainingSet(samples)
    }

    @Test
    fun testCalibrateTrainingSet_consistentRepetitionsSucceeds() {
        val samples = (1..8).map { idx ->
            StudioTestHelper.createCandidate(
                sampleCount = 20,
                peakAccel = 15.0 + (idx * 0.1),
                durationMs = 600L + (idx * 5)
            )
        }

        val outcome = CalibrationEngine.calibrateTrainingSet(samples)
        assertTrue("Calibration should succeed for consistent samples", outcome is CalibrationEngine.CalibrationOutcome.Success)

        val result = (outcome as CalibrationEngine.CalibrationOutcome.Success).result
        assertNotNull(result.medoid)
        assertTrue("Threshold should be bounded by 12.0", result.thresholdPos <= 12.0)
        assertTrue("Threshold should be at least 1.25", result.thresholdPos >= 1.25)
        assertTrue("Cluster standard deviation should be <= 3.5", result.clusterStdDev <= 3.5)
    }

    @Test
    fun testCalibrateTrainingSet_singleStrokeOmitsPause() {
        val samples = (1..8).map {
            StudioTestHelper.createCandidate(
                strokeCount = 1,
                hasPause = false,
                durationMs = 450L
            )
        }

        val outcome = CalibrationEngine.calibrateTrainingSet(samples)
        assertTrue(outcome is CalibrationEngine.CalibrationOutcome.Success)

        val result = (outcome as CalibrationEngine.CalibrationOutcome.Success).result
        assertNull("Single-stroke gestures must have pauseStdDevMs = null", result.kinematicBaselines.pauseStdDevMs)
        assertNull("Single-stroke gestures must have pauseMeanMs = null", result.kinematicBaselines.pauseMeanMs)
    }

    @Test
    fun testCalibrateTrainingSet_doubleStrokeIncludesPause() {
        val samples = (1..8).map { idx ->
            StudioTestHelper.createCandidate(
                strokeCount = 2,
                hasPause = true,
                durationMs = 650L + (idx * 5)
            )
        }

        val outcome = CalibrationEngine.calibrateTrainingSet(samples)
        assertTrue(outcome is CalibrationEngine.CalibrationOutcome.Success)

        val result = (outcome as CalibrationEngine.CalibrationOutcome.Success).result
        assertNotNull("Double-stroke gestures must compute pauseStdDevMs", result.kinematicBaselines.pauseStdDevMs)
    }

    @Test
    fun testConflictAdvisory_detectsOverlap() {
        val medoidA = StudioTestHelper.createCandidate(sampleCount = 20, peakAccel = 15.0)
        val medoidB = StudioTestHelper.createCandidate(sampleCount = 20, peakAccel = 15.2)

        val warning = CalibrationEngine.checkConflictAdvisory(
            medoidA = medoidA,
            stdDevA = 1.0,
            gestureAName = "Gesture Alpha",
            medoidB = medoidB,
            stdDevB = 1.0,
            gestureBName = "Gesture Beta"
        )

        assertNotNull("Identical/close gestures must trigger a heuristic conflict warning", warning)
        assertTrue(warning!!.contains("Conflict Advisory"))
    }
}
