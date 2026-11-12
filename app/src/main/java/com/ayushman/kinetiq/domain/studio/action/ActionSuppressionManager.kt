package com.ayushman.kinetiq.domain.studio.action

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@JvmInline
value class ActionReservationToken(val id: Long)

sealed class TrainingSessionResult {
    data class Acquired(val session: ActionSuppressionSession) : TrainingSessionResult()
    data class Rejected(val reason: String) : TrainingSessionResult()
}

interface ActionSuppressionSession : AutoCloseable {
    val sessionId: String
}

/**
 * Coordinates exclusive training sessions and identity-based action reservation barriers.
 */
class ActionSuppressionManager {
    private val lock = Any()
    private var activeOwnerSessionId: String? = null // Strictly exclusive session owner
    private var suppressionRequestCount = 0         // Reference counter for suppression requests
    private var nextReservationId = 1L              // Strictly monotonic lifetime-unique counter
    private val activeReservations = mutableSetOf<Long>() // Set of active action reservation IDs
    private val activeSessionTokens = mutableSetOf<String>()

    val isActionSuppressed: Boolean
        get() = synchronized(lock) { suppressionRequestCount > 0 || activeSessionTokens.isNotEmpty() }

    val activeReservationCount: Int
        get() = synchronized(lock) { activeReservations.size }

    /**
     * Strictly Exclusive Session Acquisition:
     * Rejects EVERY new acquisition while an active owner exists (activeOwnerSessionId != null),
     * including duplicate acquisition attempts using the exact same sessionId.
     * Dispatched in a coroutine - NEVER blocks the Android Main/UI Thread!
     */
    suspend fun acquireExclusiveSession(sessionId: String): TrainingSessionResult {
        synchronized(lock) {
            if (activeOwnerSessionId != null) {
                return TrainingSessionResult.Rejected(
                    "A training session is already active (owner: $activeOwnerSessionId). Concurrent or duplicate acquisition rejected."
                )
            }
            activeOwnerSessionId = sessionId
            suppressionRequestCount++
        }

        try {
            // Cooperative non-blocking barrier wait: ensures all in-flight reservations complete
            while (true) {
                val hasActiveReservations = synchronized(lock) { activeReservations.isNotEmpty() }
                if (!hasActiveReservations) break
                delay(10L) // Non-blocking cooperative suspension!
            }

            synchronized(lock) {
                activeSessionTokens.add(sessionId)
            }

            return TrainingSessionResult.Acquired(object : ActionSuppressionSession {
                override val sessionId: String = sessionId
                private var isClosed = false

                override fun close() {
                    synchronized(lock) {
                        if (isClosed) return
                        isClosed = true
                        if (activeOwnerSessionId == sessionId) {
                            activeOwnerSessionId = null
                        }
                        if (activeSessionTokens.remove(sessionId)) {
                            suppressionRequestCount = maxOf(0, suppressionRequestCount - 1)
                        }
                    }
                }
            })
        } catch (e: Throwable) {
            synchronized(lock) {
                if (activeOwnerSessionId == sessionId) {
                    activeOwnerSessionId = null
                }
                suppressionRequestCount = maxOf(0, suppressionRequestCount - 1)
            }
            throw e
        }
    }

    /**
     * Identity-Based Action Dispatch Reservation:
     * Atomically checks suppression request counter and active tokens.
     * Returns a unique ActionReservationToken if accepted, or null if suppressed.
     *
     * Lifetime-Uniqueness Allocation Strategy (Zero ID Reuse):
     * - Strictly monotonic: generates unique positive IDs starting from 1L up to Long.MAX_VALUE - 1L.
     * - ZERO ID REUSE: Never wraps around or reuses IDs across the entire process lifetime.
     *   Even if a stale or duplicate releaseActionReservation() is called later with an old token,
     *   it can NEVER match a newly allocated reservation because IDs are never recycled.
     * - Permanent Exhaustion Refusal: If nextReservationId reaches Long.MAX_VALUE, the ID space
     *   is exhausted. The allocator permanently refuses any new reservations by returning null (fail-safe).
     * - Invariant: Every allocated ActionReservationToken is guaranteed to be globally unique
     *   across the entire lifetime of the process.
     */
    fun tryReserveActionDispatch(): ActionReservationToken? = synchronized(lock) {
        if (suppressionRequestCount > 0 || activeSessionTokens.isNotEmpty()) {
            return null // Dropped immediately if training is requested or active
        }

        if (nextReservationId == Long.MAX_VALUE) {
            // ID space exhausted for process lifetime. Permanently refuse new reservations.
            // Guarantees zero wraparound, zero ID reuse, and complete lifetime uniqueness.
            return null
        }

        val token = ActionReservationToken(nextReservationId++)
        activeReservations.add(token.id)
        return token
    }

    /**
     * Idempotent Reservation Release:
     * Only the matching token can release its specific reservation.
     * Duplicate release of the same token is a safe no-op and never decrements another action's reservation.
     * Invariants guaranteed:
     * 1. activeReservations.size >= 0 always.
     * 2. activeReservations.size == 0 IF AND ONLY IF activeReservations is completely empty.
     */
    fun releaseActionReservation(token: ActionReservationToken?): Boolean = synchronized(lock) {
        if (token == null) return false
        return activeReservations.remove(token.id)
    }

    // For test inspection only
    internal fun setNextReservationIdForTesting(id: Long) = synchronized(lock) {
        nextReservationId = id
    }
}

