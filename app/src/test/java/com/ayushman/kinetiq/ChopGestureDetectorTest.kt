package com.ayushman.kinetiq

import com.ayushman.kinetiq.domain.gesture.ChopGestureDetector
import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureState
import com.ayushman.kinetiq.domain.model.GestureType
import com.ayushman.kinetiq.domain.model.SensitivityLevel
import com.ayushman.kinetiq.domain.model.SensorReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChopGestureDetectorTest {

    private lateinit var detector: ChopGestureDetector

    @Before
    fun setUp() {
        detector = ChopGestureDetector(SensitivityLevel.MEDIUM)
    }

    @Test
    fun testSingleChopDoesNotTriggerRecognition() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<GestureEvent>()
        val job = launch { detector.events.toList(events) }

        var t = 1_000_000_000L // 1.0s

        // Normal resting readings
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0f, 0.5f, 0.2f, t))
            t += 20_000_000L
        }

        // Single sharp chop stroke: y=16.0, z=5.0 (magnitude ~16.7 m/s^2)
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0.2f, 16.0f, 5.0f, t))
            t += 20_000_000L
        }

        // Return to neutral
        repeat(10) {
            detector.processReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))
            t += 20_000_000L
        }

        job.cancel()

        // Should not have triggered any recognized event
        assertTrue(events.none { it is GestureEvent.Recognized })
        assertTrue(detector.state.value is GestureState.WaitingForSecondMovement || detector.state.value is GestureState.Idle)
    }

    @Test
    fun testValidDoubleChopTriggersRecognition() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L // 1.0s

        // Initial rest
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0f, 0.5f, 0.2f, t))
            t += 20_000_000L
        }

        // First chop strike (magnitude ~16.8 m/s^2)
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0.5f, 15.0f, 7.5f, t))
            t += 20_000_000L
        }

        // Recoil to neutral envelope (magnitude ~1.5 m/s^2) for 160ms
        repeat(8) {
            detector.processReading(SensorReading.LinearAcceleration(0.1f, 1.2f, 0.8f, t))
            t += 20_000_000L
        }

        // Second chop strike (magnitude ~17.5 m/s^2)
        repeat(4) {
            detector.processReading(SensorReading.LinearAcceleration(0.3f, 16.0f, 7.0f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Should trigger exactly 1 recognized double chop", 1, recognizedEvents.size)
        val event = recognizedEvents[0]
        assertEquals(GestureType.DOUBLE_CHOP, event.type)
        assertTrue("Confidence should be high (> 0.7)", event.confidence > 0.7f)
        assertTrue(detector.state.value is GestureState.Cooldown)
    }

    @Test
    fun testActiveRecoilBetweenChopsIsAccepted() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // First chop strike (magnitude ~ 16.0 m/s^2)
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0f, 15.0f, 5.5f, t))
            t += 20_000_000L
        }

        // Active physical arm recoil/lift (magnitude ~ 4.5 m/s^2, which failed in legacy static 3.8 threshold)
        repeat(8) {
            detector.processReading(SensorReading.LinearAcceleration(0f, 4.2f, 1.6f, t)) // mag ~ 4.5 m/s^2
            t += 20_000_000L
        }

        // Second chop strike (magnitude ~ 16.0 m/s^2)
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0f, 15.0f, 5.5f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Double chop with natural active arm recoil (4.5 m/s^2) must be recognized", 1, recognizedEvents.size)
    }

    @Test
    fun testChopWithoutNeutralRecoilIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // First chop strike
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0.5f, 16.0f, 5.0f, t))
            t += 20_000_000L
        }

        // Continuous high vibration/shaking without returning into valley (mag ~ 11.5 > recoilThreshold)
        repeat(8) {
            detector.processReading(SensorReading.LinearAcceleration(0.5f, 10.0f, 5.5f, t))
            t += 20_000_000L
        }

        // Second stroke attempted without neutral recoil
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0.5f, 16.0f, 5.0f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Should NOT trigger without neutral recoil", 0, recognizedEvents.size)
    }

    @Test
    fun testDoubleChopWindowExpired() = runTest {
        var t = 1_000_000_000L

        // First chop strike
        repeat(3) {
            detector.processReading(SensorReading.LinearAcceleration(0.5f, 16.0f, 5.0f, t))
            t += 20_000_000L
        }

        // Neutral recoil
        repeat(5) {
            detector.processReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))
            t += 20_000_000L
        }

        // Advance time past max window (> 800ms)
        t += 900_000_000L

        // Process a reading to trigger deadline check
        detector.processReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))

        assertEquals("Should reset back to Idle after window expiration", GestureState.Idle, detector.state.value)
    }

    @Test
    fun testLateralShakeIsRejectedByAxisAlignment() = runTest {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // High acceleration along lateral X-axis (side-to-side shake, not downward chop)
        repeat(5) {
            detector.processReading(SensorReading.LinearAcceleration(18.0f, 1.0f, 1.0f, t))
            t += 20_000_000L
        }

        repeat(5) {
            detector.processReading(SensorReading.LinearAcceleration(0.5f, 0.5f, 0.5f, t))
            t += 20_000_000L
        }

        repeat(5) {
            detector.processReading(SensorReading.LinearAcceleration(18.0f, 1.0f, 1.0f, t))
            t += 20_000_000L
        }

        job.cancel()

        assertEquals("Lateral shake should be rejected", 0, recognizedEvents.size)
        assertEquals(GestureState.Idle, detector.state.value)
    }

    @Test
    fun testSensitivityChangesThresholds() {
        detector.sensitivity = SensitivityLevel.LOW
        assertEquals(18.0f, detector.sensitivity.chopPeakThreshold)

        detector.sensitivity = SensitivityLevel.HIGH
        assertEquals(10.0f, detector.sensitivity.chopPeakThreshold)
    }
}
