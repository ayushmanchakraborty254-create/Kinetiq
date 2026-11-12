package com.ayushman.kinetiq.domain.gesture

import com.ayushman.kinetiq.domain.model.GestureType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class GestureCooldownManager(
    private val defaultCooldownMs: Long = 450L
) {
    private val lastTriggerTimestampMap = ConcurrentHashMap<GestureType, Long>()

    @Volatile
    private var lastAnyTriggerTimestamp: Long = 0L

    fun canTrigger(type: GestureType, nowMs: Long = System.currentTimeMillis()): Boolean {
        // Global cross-gesture cooldown: guarantees no gesture can trigger
        // within defaultCooldownMs of ANY previously recognized gesture
        if ((nowMs - lastAnyTriggerTimestamp) < defaultCooldownMs) {
            return false
        }
        val lastTrigger = lastTriggerTimestampMap[type] ?: 0L
        return (nowMs - lastTrigger) >= defaultCooldownMs
    }

    fun recordTrigger(type: GestureType, nowMs: Long = System.currentTimeMillis()) {
        lastAnyTriggerTimestamp = nowMs
        lastTriggerTimestampMap[type] = nowMs
    }

    fun remainingCooldownMs(type: GestureType, nowMs: Long = System.currentTimeMillis()): Long {
        val lastTrigger = lastTriggerTimestampMap[type] ?: 0L
        val elapsedSpecific = nowMs - lastTrigger
        val elapsedAny = nowMs - lastAnyTriggerTimestamp

        val remSpecific = if (elapsedSpecific < defaultCooldownMs) defaultCooldownMs - elapsedSpecific else 0L
        val remAny = if (elapsedAny < defaultCooldownMs) defaultCooldownMs - elapsedAny else 0L
        return max(remSpecific, remAny)
    }

    fun reset() {
        lastAnyTriggerTimestamp = 0L
        lastTriggerTimestampMap.clear()
    }
}
