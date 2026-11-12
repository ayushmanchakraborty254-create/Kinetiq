package com.ayushman.kinetiq

import com.ayushman.kinetiq.domain.gesture.GestureEngine
import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureType
import com.ayushman.kinetiq.domain.model.SensorReading
import com.ayushman.kinetiq.domain.model.UserPreferences
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
class GestureEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var engine: GestureEngine

    @Before
    fun setUp() {
        engine = GestureEngine(testScope)
    }

    @Test
    fun testPocketCoveredSuppressesGesture() = runTest {
        val recognized = mutableListOf<GestureEvent.Recognized>()
        val job = testScope.launch {
            engine.gestureEvents.collect { recognized.add(it) }
        }

        engine.updatePreferences(
            UserPreferences(
                isMonitoringEnabled = true,
                isChopEnabled = true,
                isPocketProtectionEnabled = true
            )
        )

        var t = 1_000_000_000L

        // Proximity sensor signals covered (in pocket)
        engine.onProximityReading(SensorReading.Proximity(0.0f, 5.0f, true, t))

        // Execute double chop pattern
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }
        repeat(8) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))
            t += 20_000_000L
        }
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }

        job.cancel()

        assertEquals("Pocket protection must suppress chop recognition", 0, recognized.size)
    }

    @Test
    fun testDisabledGestureDoesNotTrigger() = runTest {
        val recognized = mutableListOf<GestureEvent.Recognized>()
        val job = testScope.launch {
            engine.gestureEvents.collect { recognized.add(it) }
        }

        // Monitoring is enabled, but Double Chop is turned OFF
        engine.updatePreferences(
            UserPreferences(
                isMonitoringEnabled = true,
                isChopEnabled = false,
                isTwistEnabled = true
            )
        )

        var t = 1_000_000_000L
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }
        repeat(8) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))
            t += 20_000_000L
        }
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }

        job.cancel()

        assertEquals("Disabled chop must not trigger any event", 0, recognized.size)
    }

    @Test
    fun testMutualExclusionLocksOutCrossGesture() = runTest {
        val recognized = mutableListOf<GestureEvent.Recognized>()
        val job = testScope.launch {
            engine.gestureEvents.collect { recognized.add(it) }
        }

        engine.updatePreferences(
            UserPreferences(
                isMonitoringEnabled = true,
                isChopEnabled = true,
                isTwistEnabled = true
            )
        )

        var t = 1_000_000_000L

        // 1. Perform complete double chop
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }
        repeat(8) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))
            t += 20_000_000L
        }
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }

        assertEquals("Double chop recognized", 1, recognized.size)
        assertEquals(GestureType.DOUBLE_CHOP, recognized[0].type)

        // 2. Immediately try to execute double twist during cross-gesture cooldown (< 1000ms)
        repeat(7) {
            engine.onGyroscopeReading(SensorReading.Gyroscope(0f, 0f, 5.0f, t))
            t += 20_000_000L
        }
        repeat(8) {
            engine.onGyroscopeReading(SensorReading.Gyroscope(0f, 0f, -0.5f, t))
            t += 20_000_000L
        }
        repeat(7) {
            engine.onGyroscopeReading(SensorReading.Gyroscope(0f, 0f, 5.0f, t))
            t += 20_000_000L
        }

        job.cancel()

        // Double twist must be locked out by the cross-gesture cooldown
        assertEquals("Double twist must NOT trigger within cooldown after double chop", 1, recognized.size)
    }

    @Test
    fun testChopOnlyEmitsChopAndTwistOnlyEmitsTwist() = runTest {
        val recognized = mutableListOf<GestureEvent.Recognized>()
        val job = testScope.launch {
            engine.gestureEvents.collect { recognized.add(it) }
        }

        engine.updatePreferences(
            UserPreferences(
                isMonitoringEnabled = true,
                isChopEnabled = true,
                isTwistEnabled = true
            )
        )

        var t = 1_000_000_000L

        // Execute double chop
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }
        repeat(8) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))
            t += 20_000_000L
        }
        repeat(3) {
            engine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }

        job.cancel()

        assertEquals(1, recognized.size)
        assertEquals(GestureType.DOUBLE_CHOP, recognized[0].type)
        assertTrue("Gesture type must strictly be DOUBLE_CHOP", recognized.none { it.type == GestureType.DOUBLE_TWIST })
    }

    @Test
    fun testSlotExclusivity_factoryDetectorUnhookedWhenPersonalizedActive() = runTest {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "kinetiq_excl_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val storage = com.ayushman.kinetiq.domain.studio.storage.GestureTemplateStorage(overrideDir = tempDir)

        // Save an enabled personalized double chop template
        val medoid = com.ayushman.kinetiq.domain.studio.StudioTestHelper.createCandidate(sampleCount = 20, strokeCount = 2)
        val template = com.ayushman.kinetiq.domain.studio.model.GestureTemplate(
            id = com.ayushman.kinetiq.domain.studio.model.GestureTemplate.CORE_CHOP_ID,
            displayName = "Personalized Double Chop",
            isCorePersonalization = true,
            requiredStrokeCount = 2,
            assignedAction = com.ayushman.kinetiq.domain.studio.model.ActionDefinition.ToggleFlashlight(),
            isEnabled = true,
            validationStatus = com.ayushman.kinetiq.domain.studio.model.ValidationStatus.PROVISIONALLY_VALIDATED,
            createdEpochMs = 1000L,
            positiveDistanceThreshold = 5.0,
            positiveClusterMean = 1.0,
            positiveClusterStdDev = 0.5,
            kinematicBaselines = com.ayushman.kinetiq.domain.studio.model.KinematicBaselines(15.0, 1.0, 3.0, 0.5, 600.0, 50.0),
            medoidTemplate = medoid,
            userNegativeTemplates = listOf(com.ayushman.kinetiq.domain.studio.StudioTestHelper.createNoiseCandidate())
        )
        storage.saveTemplate(template)

        val exclusiveEngine = GestureEngine(
            scope = testScope,
            templateStorage = storage
        )
        exclusiveEngine.updatePreferences(
            UserPreferences(
                isMonitoringEnabled = true,
                isChopEnabled = true,
                isTwistEnabled = true
            )
        )

        val recognized = mutableListOf<GestureEvent.Recognized>()
        val job = testScope.launch {
            exclusiveEngine.gestureEvents.collect { recognized.add(it) }
        }

        var t = 1_000_000_000L

        // Execute factory double chop pattern that would normally trigger factory detector
        repeat(3) {
            exclusiveEngine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }
        repeat(8) {
            exclusiveEngine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 1.0f, 0.5f, t))
            t += 20_000_000L
        }
        repeat(3) {
            exclusiveEngine.onLinearAccelerationReading(SensorReading.LinearAcceleration(0f, 16.0f, 6.0f, t))
            t += 20_000_000L
        }

        job.cancel()

        // Factory detector was unhooked! (It received 0 samples because hasPersonalizedChop is true)
        assertEquals("Factory detector must NOT trigger when personalized template is active", 0, exclusiveEngine.getChopDetector().events.replayCache.size)

        tempDir.deleteRecursively()
    }
}
