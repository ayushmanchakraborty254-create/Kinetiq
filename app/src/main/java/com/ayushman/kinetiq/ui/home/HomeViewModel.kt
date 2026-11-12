package com.ayushman.kinetiq.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayushman.kinetiq.KinetiqApplication
import com.ayushman.kinetiq.data.sensor.SensorHardwareStatus
import com.ayushman.kinetiq.domain.model.SensitivityLevel
import com.ayushman.kinetiq.domain.model.UserPreferences
import com.ayushman.kinetiq.service.GestureMonitoringService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KinetiqApplication
    private val preferencesRepository = app.preferencesRepository
    private val sensorRepository = app.sensorRepository
    private val flashlightController = app.flashlightController

    val hardwareStatus: SensorHardwareStatus = sensorRepository.getHardwareStatus()

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    val isTorchOn: StateFlow<Boolean> = flashlightController.isTorchOn

    fun toggleMasterMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMonitoringEnabled(enabled)
            if (enabled) {
                GestureMonitoringService.startService(app)
            } else {
                GestureMonitoringService.stopService(app)
            }
        }
    }

    fun toggleChop(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setChopEnabled(enabled)
        }
    }

    fun toggleTwist(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTwistEnabled(enabled)
        }
    }

    fun setChopSensitivity(level: SensitivityLevel) {
        viewModelScope.launch {
            preferencesRepository.setChopSensitivity(level)
        }
    }

    fun setTwistSensitivity(level: SensitivityLevel) {
        viewModelScope.launch {
            preferencesRepository.setTwistSensitivity(level)
        }
    }
}

