package com.ayushman.kinetiq.domain.studio.action

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.domain.studio.model.MotionCandidate
import com.ayushman.kinetiq.platform.CameraLauncher
import com.ayushman.kinetiq.platform.FlashlightController
import com.ayushman.kinetiq.platform.HapticController

/**
 * Action Executor Dispatch Implementation.
 *
 * Enforces an atomic cooldown check-and-update gate that serializes concurrent gesture
 * callbacks and updates the dispatch timestamp strictly after an action reservation is acquired.
 * Guarantees that every accepted action reservation is released in a finally block.
 */
class ActionExecutor(
    private val context: Context,
    private val suppressionManager: ActionSuppressionManager,
    private val flashlightController: FlashlightController? = null,
    private val cameraLauncher: CameraLauncher? = null,
    private val hapticController: HapticController? = null
) {
    companion object {
        private const val TAG = "KinetiqActionExecutor"
        const val COOLDOWN_MS = 450L
    }

    private val cooldownLock = Any()
    var lastActionDispatchTimestampMs: Long = 0L
        private set

    /**
     * Executes the given action definition with atomic cooldown check,
     * suppression validation, and guaranteed reservation token release.
     */
    fun executeAction(candidate: MotionCandidate, action: ActionDefinition): Boolean {
        return executeAction(candidate.physicalEndTimestampMs, action)
    }

    /**
     * Executes the given action definition with atomic cooldown check based on event timestamp ms.
     */
    fun executeAction(timestampMs: Long, action: ActionDefinition): Boolean {
        val now = timestampMs

        // 1. Atomic Cooldown & Reservation Gate
        val token: ActionReservationToken = synchronized(cooldownLock) {
            if (now - lastActionDispatchTimestampMs < COOLDOWN_MS) {
                Log.d(TAG, "Suppressed action dispatch: candidate completed within 450 ms cooldown (${now - lastActionDispatchTimestampMs} ms)")
                return false
            }

            // 2. Identity-Based Action Dispatch Reservation
            val reservation = suppressionManager.tryReserveActionDispatch()
            if (reservation == null) {
                Log.d(TAG, "Suppressed action dispatch: training mode active or reservation refused")
                return false
            }

            // 3. Atomically update timestamp ONLY AFTER reservation is successfully acquired!
            lastActionDispatchTimestampMs = now
            reservation
        }

        try {
            // 4. Hardware Execution (outside cooldownLock so hardware I/O doesn't block the lock)
            return when (action) {
                is ActionDefinition.ToggleFlashlight -> {
                    Log.i(TAG, ">>> Executing IMMUTABLE Action: Toggle Flashlight")
                    val toggled = flashlightController?.toggleFlashlight() ?: true
                    if (toggled) {
                        hapticController?.performGestureConfirmation()
                    }
                    toggled
                }

                is ActionDefinition.LaunchCamera -> {
                    Log.i(TAG, ">>> Executing IMMUTABLE Action: Launch Camera")
                    cameraLauncher?.launchCamera()
                    hapticController?.performDoublePulse()
                    true
                }

                is ActionDefinition.LaunchApp -> {
                    Log.i(TAG, ">>> Executing Custom Action: Launch App (${action.packageName})")
                    val intent = try {
                        context.packageManager?.getLaunchIntentForPackage(action.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } catch (_: Exception) {
                        null
                    }
                    if (intent != null) {
                        context.startActivity(intent)
                        hapticController?.performGestureConfirmation()
                        true
                    } else {
                        Log.w(TAG, "Could not find launch intent for package: ${action.packageName}")
                        false
                    }
                }

                is ActionDefinition.None -> {
                    Log.d(TAG, "ActionDefinition.None: No hardware action triggered")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hardware action execution failed with exception", e)
            return false
        } finally {
            // 5. Guaranteed Idempotent Reservation Release in finally (even on exception/throw)
            suppressionManager.releaseActionReservation(token)
        }
    }
}
