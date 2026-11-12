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
}
