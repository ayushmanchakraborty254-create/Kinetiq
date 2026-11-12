package com.ayushman.kinetiq.ui.calibration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayushman.kinetiq.KinetiqApplication
import com.ayushman.kinetiq.domain.calibration.GestureCalibrationManager
import com.ayushman.kinetiq.domain.model.CalibrationProfile
import com.ayushman.kinetiq.domain.model.GestureType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

enum class CalibrationStep {
    SELECT_GESTURE,
    PREPARING,
    RECORDING,
    REVIEW_PROFILE,
    COMPLETED
}

class CalibrationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KinetiqApplication
    private val sensorRepository = app.sensorRepository
    private val preferencesRepository = app.preferencesRepository
    private val calibrationManager = GestureCalibrationManager()

    private val _step = MutableStateFlow(CalibrationStep.SELECT_GESTURE)
    val step: StateFlow<CalibrationStep> = _step.asStateFlow()

    private val _selectedGesture = MutableStateFlow(GestureType.DOUBLE_CHOP)
    val selectedGesture: StateFlow<GestureType> = _selectedGesture.asStateFlow()

    private val _currentSampleCount = MutableStateFlow(0)
    val currentSampleCount: StateFlow<Int> = _currentSampleCount.asStateFlow()

    private val _liveSensorValue = MutableStateFlow(0f)
    val liveSensorValue: StateFlow<Float> = _liveSensorValue.asStateFlow()

    private val _calculatedProfile = MutableStateFlow<CalibrationProfile?>(null)
    val calculatedProfile: StateFlow<CalibrationProfile?> = _calculatedProfile.asStateFlow()

    private var sensorJob: Job? = null
    private val targetSampleCount = 3
    private var currentAttemptPeak = 0f

    fun selectGesture(type: GestureType) {
        _selectedGesture.value = type
        _step.value = CalibrationStep.PREPARING
    }

    fun startRecording() {
        _step.value = CalibrationStep.RECORDING
        _currentSampleCount.value = 0
        currentAttemptPeak = 0f
        calibrationManager.startCalibration()

        sensorJob?.cancel()
        when (_selectedGesture.value) {
            GestureType.DOUBLE_CHOP -> {
                sensorJob = viewModelScope.launch {
                    sensorRepository.linearAccelerationFlow().collect { reading ->
                        val mag = reading.magnitude
                        _liveSensorValue.value = mag

                        if (mag > currentAttemptPeak) {
                            currentAttemptPeak = mag
                        }

                        // When user drops back to neutral after a peak > 8.0
                        if (currentAttemptPeak > 8.0f && mag < 4.0f) {
                            calibrationManager.recordPeak(currentAttemptPeak)
                            _currentSampleCount.value = calibrationManager.getSampleCount()
                            currentAttemptPeak = 0f

                            if (calibrationManager.getSampleCount() >= targetSampleCount) {
                                completeRecording()
                            }
                        }
                    }
                }
            }

            GestureType.DOUBLE_TWIST -> {
                sensorJob = viewModelScope.launch {
                    sensorRepository.gyroscopeFlow().collect { reading ->
                        val speed = max(abs(reading.z), abs(reading.y))
                        _liveSensorValue.value = speed

                        if (speed > currentAttemptPeak) {
                            currentAttemptPeak = speed
                        }

                        // When user slows down after a twist > 2.5 rad/s
                        if (currentAttemptPeak > 2.5f && speed < 1.0f) {
                            calibrationManager.recordPeak(currentAttemptPeak)
                            _currentSampleCount.value = calibrationManager.getSampleCount()
                            currentAttemptPeak = 0f

                            if (calibrationManager.getSampleCount() >= targetSampleCount) {
                                completeRecording()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun completeRecording() {
        sensorJob?.cancel()
        val profile = calibrationManager.calculateProfile(_selectedGesture.value)
        _calculatedProfile.value = profile
        _step.value = CalibrationStep.REVIEW_PROFILE
    }

    fun applyCalibration() {
        val profile = _calculatedProfile.value ?: return
        viewModelScope.launch {
            when (profile.gestureType) {
                GestureType.DOUBLE_CHOP -> {
                    preferencesRepository.setCustomChopThreshold(profile.recommendedPeakThreshold)
                }
                GestureType.DOUBLE_TWIST -> {
                    preferencesRepository.setCustomTwistThreshold(profile.recommendedPeakThreshold)
                }
            }
            _step.value = CalibrationStep.COMPLETED
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            preferencesRepository.resetCalibration()
            _step.value = CalibrationStep.SELECT_GESTURE
        }
    }

    fun resetFlow() {
        sensorJob?.cancel()
        calibrationManager.reset()
        _step.value = CalibrationStep.SELECT_GESTURE
        _currentSampleCount.value = 0
        _calculatedProfile.value = null
    }

    override fun onCleared() {
        sensorJob?.cancel()
        super.onCleared()
    }
}

