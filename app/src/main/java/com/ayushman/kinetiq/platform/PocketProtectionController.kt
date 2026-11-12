package com.ayushman.kinetiq.platform

import android.os.SystemClock

class PocketProtectionController(
    private val initializationGracePeriodMs: Long = 600L
) {
    @Volatile
    private var isCovered: Boolean = false

    @Volatile
    private var serviceStartedAtElapsedMs: Long = SystemClock.elapsedRealtime()

    fun onServiceStarted() {
        serviceStartedAtElapsedMs = SystemClock.elapsedRealtime()
    }

    fun onProximityChanged(isNear: Boolean) {
        isCovered = isNear
    }

    fun isGestureBlocked(): Boolean {
        // Block if proximity sensor detects screen is covered (in pocket or face down)
        if (isCovered) return true

        // Block during immediate initialization period after monitoring starts or phone is moved
        val elapsedSinceStart = SystemClock.elapsedRealtime() - serviceStartedAtElapsedMs
        return elapsedSinceStart < initializationGracePeriodMs
    }

    fun reset() {
        isCovered = false
        serviceStartedAtElapsedMs = SystemClock.elapsedRealtime()
    }
}

