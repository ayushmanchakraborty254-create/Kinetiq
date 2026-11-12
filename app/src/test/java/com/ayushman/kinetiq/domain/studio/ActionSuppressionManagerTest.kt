package com.ayushman.kinetiq.domain.studio

import com.ayushman.kinetiq.domain.studio.action.ActionSuppressionManager
import com.ayushman.kinetiq.domain.studio.action.TrainingSessionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionSuppressionManagerTest {

    private lateinit var manager: ActionSuppressionManager

    @Before
    fun setUp() {
        manager = ActionSuppressionManager()
    }

    @Test
    fun testExclusiveAcquisition_grantsFirstAndRejectsSecond() = runTest {
        val result1 = manager.acquireExclusiveSession("session_A")
        assertTrue("First session must be acquired", result1 is TrainingSessionResult.Acquired)
        assertTrue(manager.isActionSuppressed)

        // Competing session with different ID must be rejected
        val result2 = manager.acquireExclusiveSession("session_B")
        assertTrue("Competing session must be rejected", result2 is TrainingSessionResult.Rejected)

        // Repeated acquisition attempt with the exact SAME sessionId must also be rejected
        val result3 = manager.acquireExclusiveSession("session_A")
        assertTrue("Duplicate same-ID acquisition must be rejected", result3 is TrainingSessionResult.Rejected)

        // Close session A
        (result1 as TrainingSessionResult.Acquired).session.close()
        assertFalse(manager.isActionSuppressed)

        // Now session B can acquire
        val result4 = manager.acquireExclusiveSession("session_B")
        assertTrue(result4 is TrainingSessionResult.Acquired)
        (result4 as TrainingSessionResult.Acquired).session.close()
    }

    @Test
    fun testActionReservation_suppressedWhenTrainingActive() = runTest {
        val tokenBefore = manager.tryReserveActionDispatch()
        assertNotNull("Should reserve action when no training is active", tokenBefore)
        manager.releaseActionReservation(tokenBefore)

        val sessionResult = manager.acquireExclusiveSession("session_1")
        assertTrue(sessionResult is TrainingSessionResult.Acquired)

        val tokenDuring = manager.tryReserveActionDispatch()
        assertNull("Action reservation must be refused while training is active", tokenDuring)

        (sessionResult as TrainingSessionResult.Acquired).session.close()

        val tokenAfter = manager.tryReserveActionDispatch()
        assertNotNull("Action reservation should succeed after training closes", tokenAfter)
        manager.releaseActionReservation(tokenAfter)
    }

    @Test
    fun testMonotonicReservationId_permanentRefusalAtLongMax() {
        manager.setNextReservationIdForTesting(Long.MAX_VALUE)

        val token = manager.tryReserveActionDispatch()
        assertNull("Allocator must permanently refuse reservations when Long ID space is exhausted", token)
        assertEquals(0, manager.activeReservationCount)
    }

    @Test
    fun testIdempotentReservationRelease() {
        val token1 = manager.tryReserveActionDispatch()
        val token2 = manager.tryReserveActionDispatch()
        assertNotNull(token1)
        assertNotNull(token2)
        assertEquals(2, manager.activeReservationCount)

        // Release token 1
        val firstRelease = manager.releaseActionReservation(token1)
        assertTrue("First release must return true", firstRelease)
        assertEquals(1, manager.activeReservationCount)

        // Duplicate release of token 1
        val duplicateRelease = manager.releaseActionReservation(token1)
        assertFalse("Duplicate release must return false and be a safe no-op", duplicateRelease)
        assertEquals(1, manager.activeReservationCount)

        // Release token 2
        manager.releaseActionReservation(token2)
        assertEquals(0, manager.activeReservationCount)
    }

    @Test
    fun testCoroutineBarrier_waitsForInFlightActionReservations() = runTest {
        val token = manager.tryReserveActionDispatch()
        assertNotNull(token)
        assertEquals(1, manager.activeReservationCount)

        var acquisitionCompleted = false
        val job = launch {
            val result = manager.acquireExclusiveSession("session_barrier")
            if (result is TrainingSessionResult.Acquired) {
                acquisitionCompleted = true
                result.session.close()
            }
        }

        advanceTimeBy(50)
        assertFalse("Session acquisition must wait while in-flight action reservation is held", acquisitionCompleted)

        // Release in-flight reservation
        manager.releaseActionReservation(token)
        advanceTimeBy(50)

        assertTrue("Session acquisition must proceed once barrier is cleared", acquisitionCompleted)
        job.cancel()
    }
}

