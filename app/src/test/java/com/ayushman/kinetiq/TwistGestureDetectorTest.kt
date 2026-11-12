package com.ayushman.kinetiq

import com.ayushman.kinetiq.domain.gesture.TwistGestureDetector
import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureState
import com.ayushman.kinetiq.domain.model.GestureType
import com.ayushman.kinetiq.domain.model.SensitivityLevel
import com.ayushman.kinetiq.domain.model.SensorReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwistGestureDetectorTest {

    private lateinit var detector: TwistGestureDetector

    @Before
    fun setUp() {
        detector = TwistGestureDetector(SensitivityLevel.MEDIUM)
    }

    @Test
    fun testValidDoubleTwistTriggersRecognition() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Resting
        repeat(3) {
            detector.processReading(SensorReading.Gyroscope(0f, 0.1f, 0.05f, t))
            t += 20_000_000L
        }

        // First twist: angular velocity ~ 5.0 rad/s over 140ms
        // Displacing ~ 0.7 radians (~ 40 degrees)
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0.2f, 0.1f, 5.0f, t))
            t += 20_000_000L
        }

        // Neutral reversal/pause (160ms)
        repeat(8) {
            detector.processReading(SensorReading.Gyroscope(0.1f, 0.1f, -0.5f, t))
            t += 20_000_000L
        }

        // Second twist: angular velocity ~ 5.2 rad/s over 140ms
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0.2f, 0.1f, 5.2f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Should trigger exactly 1 recognized double twist", 1, recognizedEvents.size)
        val event = recognizedEvents[0]
        assertEquals(GestureType.DOUBLE_TWIST, event.type)
        assertTrue("Confidence should be high (> 0.7)", event.confidence > 0.7f)
        assertTrue(detector.state.value is GestureState.Cooldown)
    }

    @Test
    fun testLowAngularVelocityRejected() = runTest {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Walking or slow phone turning: angular velocity ~ 1.5 rad/s (< threshold of 4.2)
        repeat(20) {
            detector.processReading(SensorReading.Gyroscope(0.2f, 0.2f, 1.5f, t))
            t += 20_000_000L
        }

        job.cancel()

        assertEquals("Slow rotation must not trigger double twist", 0, recognizedEvents.size)
        assertEquals(GestureState.Idle, detector.state.value)
    }

    @Test
    fun testTwistCooldownPreventsDuplicateLaunch() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Execute successful double twist
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0f, 0f, 5.0f, t))
            t += 20_000_000L
        }
        repeat(8) {
            detector.processReading(SensorReading.Gyroscope(0f, 0f, -0.5f, t))
            t += 20_000_000L
        }
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0f, 0f, 5.0f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        assertEquals("First double twist recognized", 1, recognizedEvents.size)

        // Try repeating movements immediately during cooldown (within 500ms < 1200ms cooldown)
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0f, 0f, 5.0f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Cooldown must prevent immediate duplicate event", 1, recognizedEvents.size)
    }

    @Test
    fun testSingleWristFlickWithRecoil_doesNotTriggerCamera() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Initial rest
        repeat(3) {
            detector.processReading(SensorReading.Gyroscope(0f, 0f, 0.05f, t))
            t += 20_000_000L
        }

        // Single flick outward: high angular velocity ~ 5.5 rad/s over 140ms (~ 44 deg)
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0.1f, 0.1f, 5.5f, t))
            t += 20_000_000L
        }

        // Natural recoil/return of that same flick: reverse velocity ~ -5.0 rad/s over 140ms
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0.1f, 0.1f, -5.0f, t))
            t += 20_000_000L
        }

        // Phone held still at rest (user does not twist again, window expires past 800ms)
        repeat(45) {
            detector.processReading(SensorReading.Gyroscope(0f, 0f, 0.05f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(
            "A single wrist flick followed by recoil must NEVER launch camera",
            0,
            recognizedEvents.size
        )
        assertEquals("Detector must reset to Idle after single flick window expires", GestureState.Idle, detector.state.value)
    }

    @Test
    fun testTwoIntentionalWristTwists_triggersCamera() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Twist 1 (outward): ~ 5.5 rad/s over 140ms
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0.1f, 0.1f, 5.5f, t))
            t += 20_000_000L
        }

        // Recoil 1 (return towards neutral): ~ -4.5 rad/s over 140ms
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0.1f, 0.1f, -4.5f, t))
            t += 20_000_000L
        }

        // Twist 2 (second intentional outward twist): ~ 5.5 rad/s over 140ms
        repeat(7) {
            detector.processReading(SensorReading.Gyroscope(0.1f, 0.1f, 5.5f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Two intentional wrist twists must trigger camera launch", 1, recognizedEvents.size)
        assertEquals(GestureType.DOUBLE_TWIST, recognizedEvents[0].type)
        assertTrue(detector.state.value is GestureState.Cooldown)
    }

    @Test
    fun testChopChopMovement_doesNotTriggerCamera() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Chop 1: primarily linear motion, low angular velocity on gyro (~ 1.2 rad/s, displacement < 10 deg)
        repeat(4) {
            detector.processReading(SensorReading.Gyroscope(0.5f, 1.2f, 0.8f, t))
            t += 20_000_000L
        }
        repeat(4) {
            detector.processReading(SensorReading.Gyroscope(0.2f, -0.8f, -0.4f, t))
            t += 20_000_000L
        }

        // Pause
        repeat(5) {
            detector.processReading(SensorReading.Gyroscope(0.1f, 0.1f, 0.1f, t))
            t += 20_000_000L
        }

        // Chop 2: second linear chop
        repeat(4) {
            detector.processReading(SensorReading.Gyroscope(0.6f, 1.4f, 0.9f, t))
            t += 20_000_000L
        }
        repeat(4) {
            detector.processReading(SensorReading.Gyroscope(0.2f, -0.9f, -0.5f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Chop-chop movement must NEVER launch camera", 0, recognizedEvents.size)
        assertEquals(GestureState.Idle, detector.state.value)
    }

    @Test
    fun testPhonePickupAndPlacement_doesNotTriggerCamera() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Phone pickup: single upward tilt ~ 3.0 rad/s for 100ms
        repeat(5) {
            detector.processReading(SensorReading.Gyroscope(0.2f, 3.0f, 0.5f, t))
            t += 20_000_000L
        }

        // Settling at eye level
        repeat(20) {
            detector.processReading(SensorReading.Gyroscope(0.05f, 0.1f, 0.05f, t))
            t += 20_000_000L
        }

        // Placement down on table: slight tilt down then stop
        repeat(4) {
            detector.processReading(SensorReading.Gyroscope(0.1f, -2.2f, -0.3f, t))
            t += 20_000_000L
        }
        repeat(25) {
            detector.processReading(SensorReading.Gyroscope(0f, 0f, 0f, t))
            t += 20_000_000L
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Phone pickup and placement must not trigger camera", 0, recognizedEvents.size)
        assertEquals(GestureState.Idle, detector.state.value)
    }

    @Test
    fun testOrdinaryHandling_doesNotTriggerCamera() = runTest(UnconfinedTestDispatcher()) {
        val recognizedEvents = mutableListOf<GestureEvent.Recognized>()
        val job = launch {
            detector.events.collect {
                if (it is GestureEvent.Recognized) recognizedEvents.add(it)
            }
        }

        var t = 1_000_000_000L

        // Casual handling: shifting grip, walking, looking at screen (angular velocity 0.3 to 1.8 rad/s)
        val speeds = listOf(0.4f, 0.8f, 1.5f, 1.2f, 0.7f, 0.3f, 1.1f, 1.8f, 0.9f, 0.5f)
        for (speed in speeds) {
            repeat(4) {
                detector.processReading(SensorReading.Gyroscope(0.2f, speed, speed * 0.8f, t))
                t += 20_000_000L
            }
        }

        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("Ordinary handling must not trigger camera", 0, recognizedEvents.size)
        assertEquals(GestureState.Idle, detector.state.value)
    }
}
