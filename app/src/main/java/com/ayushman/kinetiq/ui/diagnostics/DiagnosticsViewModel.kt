package com.ayushman.kinetiq.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayushman.kinetiq.KinetiqApplication
import com.ayushman.kinetiq.data.sensor.SensorHardwareStatus
import com.ayushman.kinetiq.data.sensor.SensorSamplingRate
import com.ayushman.kinetiq.data.sensor.SensorTraceRecorder
import com.ayushman.kinetiq.data.sensor.TraceFileInfo
import com.ayushman.kinetiq.domain.gesture.ChopGestureDetector
import com.ayushman.kinetiq.domain.gesture.TwistGestureDetector
import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureState
import com.ayushman.kinetiq.domain.model.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveSensorReadout(
    val linearAccelMagnitude: Float = 0f,
    val linearAccelLongitudinal: Float = 0f,
    val gyroSpeed: Float = 0f,
    val gyroZ: Float = 0f,
    val isProximityNear: Boolean = false
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KinetiqApplication
    private val sensorRepo = app.sensorRepository
    private val prefsRepo = app.preferencesRepository

    val hardwareStatus: SensorHardwareStatus = sensorRepo.getHardwareStatus()

    val userPreferences: StateFlow<UserPreferences> = prefsRepo.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    private val _liveSensorReadout = MutableStateFlow(LiveSensorReadout())
    val liveSensorReadout: StateFlow<LiveSensorReadout> = _liveSensorReadout.asStateFlow()

    private val _chopState = MutableStateFlow<GestureState>(GestureState.Idle)
    val chopState: StateFlow<GestureState> = _chopState.asStateFlow()

    private val _twistState = MutableStateFlow<GestureState>(GestureState.Idle)
    val twistState: StateFlow<GestureState> = _twistState.asStateFlow()

    private val _lastRecognizedGesture = MutableStateFlow<GestureEvent.Recognized?>(null)
    val lastRecognizedGesture: StateFlow<GestureEvent.Recognized?> = _lastRecognizedGesture.asStateFlow()

    val traceRecorder = SensorTraceRecorder(application, sensorRepo, viewModelScope)

    val isRecording: StateFlow<Boolean> = traceRecorder.isRecording
    val recordedSampleCount: StateFlow<Int> = traceRecorder.sampleCount
    val recordingElapsedMs: StateFlow<Long> = traceRecorder.elapsedMs
    val currentTraceLabel: StateFlow<String> = traceRecorder.currentLabel
    val selectedSamplingRate: StateFlow<SensorSamplingRate> = traceRecorder.selectedRate
    val lastSavedTrace: StateFlow<TraceFileInfo?> = traceRecorder.lastSavedTrace
    val savedTraces: StateFlow<List<TraceFileInfo>> = traceRecorder.savedTraces

    private val chopDetector = ChopGestureDetector()
    private val twistDetector = TwistGestureDetector()

    private var activeJob: Job? = null

    init {
        startLiveMonitoring()
    }

    private fun startLiveMonitoring() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            launch {
                chopDetector.events.collect { event ->
                    if (event is GestureEvent.Recognized) {
                        _lastRecognizedGesture.value = event
                    }
                }
            }
            launch {
                twistDetector.events.collect { event ->
                    if (event is GestureEvent.Recognized) {
                        _lastRecognizedGesture.value = event
                    }
                }
            }
            launch {
                chopDetector.state.collect { _chopState.value = it }
            }
            launch {
                twistDetector.state.collect { _twistState.value = it }
            }

            // Stream live sensor data for diagnostic UI
            launch {
                sensorRepo.linearAccelerationFlow().collect { reading ->
                    chopDetector.processReading(reading)
                    _liveSensorReadout.value = _liveSensorReadout.value.copy(
                        linearAccelMagnitude = reading.magnitude,
                        linearAccelLongitudinal = reading.longitudinalMagnitude
                    )
                }
            }
            launch {
                sensorRepo.gyroscopeFlow().collect { reading ->
                    twistDetector.processReading(reading)
                    _liveSensorReadout.value = _liveSensorReadout.value.copy(
                        gyroSpeed = reading.angularSpeed,
                        gyroZ = reading.z
                    )
                }
            }
            launch {
                sensorRepo.proximityFlow().collect { reading ->
                    _liveSensorReadout.value = _liveSensorReadout.value.copy(
                        isProximityNear = reading.isNear
                    )
                }
            }
        }
    }

    fun startTraceRecording(label: String? = null) {
        traceRecorder.startRecording(label)
    }

    fun stopTraceRecording() {
        traceRecorder.stopRecording()
    }

    fun setTraceLabel(label: String) {
        traceRecorder.setLabel(label)
    }

    fun setSamplingRate(rate: SensorSamplingRate) {
        traceRecorder.setSamplingRate(rate)
    }

    fun deleteTrace(fileName: String) {
        traceRecorder.deleteTrace(fileName)
    }

    fun refreshTraces() {
        traceRecorder.refreshSavedTraces()
    }

    fun resetDiagnostics() {
        _lastRecognizedGesture.value = null
        chopDetector.reset()
        twistDetector.reset()
    }

    override fun onCleared() {
        activeJob?.cancel()
        super.onCleared()
    }
}

