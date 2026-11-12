package com.ayushman.kinetiq.platform

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlashlightController(private val context: Context) {

    companion object {
        private const val TAG = "KinetiqFlashlight"
        private const val MIN_TOGGLE_DEBOUNCE_MS = 250L
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var targetCameraId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastToggleEpochMs: Long = 0L

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == targetCameraId) {
                _isTorchOn.value = enabled
                _isAvailable.value = true
                Log.d(TAG, "Hardware torch state updated: enabled=$enabled")
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == targetCameraId) {
                _isTorchOn.value = false
                _isAvailable.value = false
                Log.d(TAG, "Hardware torch unavailable for camera $cameraId")
            }
        }
    }

    init {
        initializeCamera()
    }

    private fun initializeCamera() {
        val cm = cameraManager ?: run {
            _isAvailable.value = false
            return
        }

        try {
            for (id in cm.cameraIdList) {
                val characteristics = cm.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = id
                    _isAvailable.value = true
                    Log.i(TAG, "Identified rear flashlight on camera ID: $id")
                    break
                }
            }

            // Fallback: any camera with flash
            if (targetCameraId == null) {
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (hasFlash) {
                        targetCameraId = id
                        _isAvailable.value = true
                        Log.i(TAG, "Identified fallback flashlight on camera ID: $id")
                        break
                    }
                }
            }

            cm.registerTorchCallback(torchCallback, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera for torch", e)
            _isAvailable.value = false
        }
    }

    /**
     * Toggles rear flashlight state immediately.
     * Prevents duplicate rapid toggles and synchronizes state via CameraManager.
     * Returns true if operation succeeded, false otherwise.
     */
    @Synchronized
    fun toggleFlashlight(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastToggleEpochMs < MIN_TOGGLE_DEBOUNCE_MS) {
            Log.d(TAG, "Ignored duplicate toggle: too rapid (${now - lastToggleEpochMs}ms < ${MIN_TOGGLE_DEBOUNCE_MS}ms)")
            return false
        }

        val cm = cameraManager ?: run {
            Log.w(TAG, "CameraManager not available")
            return false
        }
        val cameraId = targetCameraId ?: run {
            Log.w(TAG, "No flash-capable camera identified")
            return false
        }

        val targetState = !_isTorchOn.value
        return try {
            cm.setTorchMode(cameraId, targetState)
            _isTorchOn.value = targetState
            lastToggleEpochMs = now
            Log.i(TAG, "Flashlight setTorchMode($cameraId, $targetState) executed successfully")
            true
        } catch (e: CameraAccessException) {
            _isAvailable.value = false
            Log.e(TAG, "CameraAccessException setting torch mode", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error setting torch mode", e)
            false
        }
    }

    fun setTorch(enabled: Boolean): Boolean {
        val cm = cameraManager ?: return false
        val cameraId = targetCameraId ?: return false

        return try {
            cm.setTorchMode(cameraId, enabled)
            _isTorchOn.value = enabled
            lastToggleEpochMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed setTorch($enabled)", e)
            false
        }
    }

    fun release() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
    }
}

