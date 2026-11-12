package com.ayushman.kinetiq.domain.studio

import com.ayushman.kinetiq.domain.studio.engine.MotionSegmenter
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MotionSegmenterTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var segmenter: MotionSegmenter

    @Before
    fun setUp() {
        segmenter = MotionSegmenter(
            scope = testScope,
            sensorManager = null,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun testIdleState_triggersOnlyOnSufficientMotionPower() {
        var tNs = 1_000_000_000L

        // Low ambient noise: ax=0.1, energy well below E_trigger (0.50)
        segmenter.feedLinearAcceleration(0.1, 0.1, 0.0, tNs)
        segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        assertEquals(MotionSegmenter.State.IDLE, segmenter.currentState)

        // Vigorous gesture: ay=12.0 m/s^2 -> energy > 1.2 > 0.50
        tNs += 20_000_000L
        segmenter.feedLinearAcceleration(0.0, 12.0, 0.0, tNs)
        segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        assertEquals(MotionSegmenter.State.STROKE_1_ACTIVE, segmenter.currentState)
    }

    @Test
    fun testHardTimeoutSafety_strictlyDiscardsAt1400ms() = runTest {
        val candidates = mutableListOf<MotionCandidate>()
        val job = testScope.launch {
            segmenter.candidateEvents.collect { candidates.add(it) }
        }

        var tNs = 1_000_000_000L

        // Trigger stroke 1
        segmenter.feedLinearAcceleration(0.0, 10.0, 0.0, tNs)
        segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        assertEquals(MotionSegmenter.State.STROKE_1_ACTIVE, segmenter.currentState)

        // Continuous unsettled movement for 1380 ms (below 1400 ms timeout)
        for (i in 1..69) {
            tNs += 20_000_000L // +20 ms per step (69 * 20 = 1380 ms)
            segmenter.feedLinearAcceleration(0.0, 8.0, 0.0, tNs)
            segmenter.feedGyroscope(0.0, 1.0, 0.0, tNs)
        }
        assertEquals(MotionSegmenter.State.STROKE_1_ACTIVE, segmenter.currentState)

        // Advance to 1400 ms: dtHard = 1400 ms >= 1400 ms -> hard timeout strictly discards candidate
        tNs += 20_000_000L
        segmenter.feedLinearAcceleration(0.0, 8.0, 0.0, tNs)

        assertEquals("Segmenter must reset to IDLE after 1400 ms hard timeout", MotionSegmenter.State.IDLE, segmenter.currentState)
        assertEquals("Zero candidates must be emitted when hard timeout is reached while active/unsettled", 0, candidates.size)

        job.cancel()
    }

    @Test
    fun testDoubleStrokeRecognition_emitsTwoStrokeCandidate() = runTest {
        val candidates = mutableListOf<MotionCandidate>()
        val job = testScope.launch {
            segmenter.candidateEvents.collect { candidates.add(it) }
        }

        var tNs = 1_000_000_000L

        // 1. Stroke 1 active (200 ms)
        for (i in 1..10) {
            tNs += 20_000_000L
            segmenter.feedLinearAcceleration(0.0, 15.0, 0.0, tNs)
            segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        }

        // Stroke 1 end (energy drops below E_settle = 0.18)
        tNs += 20_000_000L
        segmenter.feedLinearAcceleration(0.0, 0.1, 0.0, tNs)
        segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        assertEquals(MotionSegmenter.State.STROKE_1_PENDING, segmenter.currentState)

        // 2. Inter-stroke pause (100 ms <= 350 ms window)
        for (i in 1..5) {
            tNs += 20_000_000L
            segmenter.feedLinearAcceleration(0.0, 0.1, 0.0, tNs)
            segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        }

        // 3. Stroke 2 active (arrives at dt = 120 ms <= 350 ms)
        for (i in 1..10) {
            tNs += 20_000_000L
            segmenter.feedLinearAcceleration(0.0, 15.0, 0.0, tNs)
            segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        }
        assertEquals(MotionSegmenter.State.STROKE_2_ACTIVE, segmenter.currentState)

        // 4. Settling phase: quiet for 240 ms (> 150 ms consecutive quiet)
        for (i in 1..12) {
            tNs += 20_000_000L
            segmenter.feedLinearAcceleration(0.0, 0.05, 0.0, tNs)
            segmenter.feedGyroscope(0.0, 0.0, 0.0, tNs)
        }

        assertEquals("Should return to IDLE after valid candidate emission", MotionSegmenter.State.IDLE, segmenter.currentState)
        assertEquals("Exactly one double-stroke candidate must be emitted", 1, candidates.size)

        val candidate = candidates[0]
        assertEquals(2, candidate.strokeCount)
        assertTrue(candidate.hasInterStrokePause)

        job.cancel()
    }
}
