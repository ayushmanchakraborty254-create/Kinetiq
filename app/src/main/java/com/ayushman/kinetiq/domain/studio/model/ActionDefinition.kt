package com.ayushman.kinetiq.domain.studio.model

/**
 * Sealed hierarchy representing all supported actions in KINETIQ Gesture Studio.
 *
 * Core gestures (Double Chop, Double Twist) have isImmutable = true and can NEVER
 * be remapped to an alternate action. Custom gestures can assign any supported action.
 */
sealed class ActionDefinition {
    abstract val isImmutable: Boolean
    abstract val actionName: String

    data class ToggleFlashlight(override val isImmutable: Boolean = true) : ActionDefinition() {
        override val actionName: String = "Toggle Flashlight"
    }

    data class LaunchCamera(override val isImmutable: Boolean = true) : ActionDefinition() {
        override val actionName: String = "Launch Camera"
    }

    data class LaunchApp(
        val packageName: String,
        val appLabel: String? = null,
        override val isImmutable: Boolean = false
    ) : ActionDefinition() {
        override val actionName: String = appLabel?.let { "Launch $it" } ?: "Launch $packageName"
    }

    data object None : ActionDefinition() {
        override val isImmutable: Boolean = false
        override val actionName: String = "None"
    }
}

