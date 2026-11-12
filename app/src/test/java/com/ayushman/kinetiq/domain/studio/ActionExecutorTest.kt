package com.ayushman.kinetiq.domain.studio

import android.content.Context
import android.content.ContextWrapper
import com.ayushman.kinetiq.domain.studio.action.ActionExecutor
import com.ayushman.kinetiq.domain.studio.action.ActionSuppressionManager
import com.ayushman.kinetiq.domain.studio.action.TrainingSessionResult
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionExecutorTest {

    private lateinit var suppressionManager: ActionSuppressionManager
    private lateinit var executor: ActionExecutor

    private class TestContext : ContextWrapper(null)

    @Before
    fun setUp() {
        suppressionManager = ActionSuppressionManager()
        executor = ActionExecutor(
            context = TestContext(),
            suppressionManager = suppressionManager,
            flashlightController = null,
            cameraLauncher = null
        )
    }

    @Test
    fun testCooldownGate_suppressesWithin450ms() {
        val action = ActionDefinition.ToggleFlashlight()

        // First action at t=1000 ms
        val firstResult = executor.executeAction(1000L, action)
        assertTrue("First action must execute", firstResult)
        assertEquals(1000L, executor.lastActionDispatchTimestampMs)
        assertEquals("Active reservation must be released in finally", 0, suppressionManager.activeReservationCount)

        // Second action at t=1300 ms (delta = 300 ms < 450 ms cooldown)
        val rapidResult = executor.executeAction(1300L, action)
        assertFalse("Rapid action within 450 ms must be suppressed by cooldown gate", rapidResult)
        assertEquals("Cooldown timestamp must NOT update on suppressed execution", 1000L, executor.lastActionDispatchTimestampMs)
        assertEquals(0, suppressionManager.activeReservationCount)

        // Third action at t=1500 ms (delta = 500 ms >= 450 ms cooldown)
        val thirdResult = executor.executeAction(1500L, action)
        assertTrue("Action after 450 ms cooldown must execute", thirdResult)
        assertEquals(1500L, executor.lastActionDispatchTimestampMs)
        assertEquals(0, suppressionManager.activeReservationCount)
    }

    @Test
    fun testSuppressionActive_doesNotUpdateCooldownTimestamp() = runTest {
        val action = ActionDefinition.LaunchCamera()

        // Acquire exclusive training session
        val sessionResult = suppressionManager.acquireExclusiveSession("training_session_1")
        assertTrue(sessionResult is TrainingSessionResult.Acquired)

        // Attempt dispatch at t=2000 ms during training session
        val resultDuringTraining = executor.executeAction(2000L, action)
        assertFalse("Action must be refused while training session is active", resultDuringTraining)
        assertEquals("Timestamp must remain 0L when reservation is refused", 0L, executor.lastActionDispatchTimestampMs)
        assertEquals(0, suppressionManager.activeReservationCount)

        // End training session
        (sessionResult as TrainingSessionResult.Acquired).session.close()

        // Subsequent dispatch at t=2100 ms must succeed immediately without false cooldown lockout
        val resultAfterTraining = executor.executeAction(2100L, action)
        assertTrue("Action must execute after training session ends", resultAfterTraining)
        assertEquals(2100L, executor.lastActionDispatchTimestampMs)
        assertEquals(0, suppressionManager.activeReservationCount)
    }
}

