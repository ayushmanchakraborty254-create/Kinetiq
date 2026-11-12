package com.ayushman.kinetiq.ui.studio

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayushman.kinetiq.KinetiqApplication
import com.ayushman.kinetiq.domain.studio.engine.MotionSegmenter
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import com.ayushman.kinetiq.domain.studio.model.TrainingStep
import com.ayushman.kinetiq.domain.studio.training.GestureTrainingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GestureStudioViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KinetiqStudioVM"
    }

    private val app = application as KinetiqApplication
    private val storage = app.gestureTemplateStorage
    private val suppressionManager = app.actionSuppressionManager
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _templates = MutableStateFlow<List<GestureTemplate>>(emptyList())
    val templates: StateFlow<List<GestureTemplate>> = _templates.asStateFlow()

    private val _activeSession = MutableStateFlow<GestureTrainingSession?>(null)
    val activeSession: StateFlow<GestureTrainingSession?> = _activeSession.asStateFlow()

    private val _trainingStep = MutableStateFlow<TrainingStep>(TrainingStep.Idle)
    val trainingStep: StateFlow<TrainingStep> = _trainingStep.asStateFlow()

    private val _rejectionMessage = MutableStateFlow<String?>(null)
    val rejectionMessage: StateFlow<String?> = _rejectionMessage.asStateFlow()

    private var motionSegmenter: MotionSegmenter? = null
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = storage.loadAllTemplates()
            _templates.value = list
        }
    }

    fun deleteTemplate(gestureId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteTemplate(gestureId)
            loadTemplates()
        }
    }

    fun resetToFactory(gestureId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteTemplate(gestureId)
            loadTemplates()
        }
    }

    fun toggleTemplateEnabled(gestureId: String, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = storage.loadTemplate(gestureId)
            if (current != null) {
                val updated = current.copy(isEnabled = isEnabled)
                storage.saveTemplate(updated)
                loadTemplates()
            }
        }
    }

    suspend fun startTraining(
        gestureId: String,
        displayName: String,
        isCore: Boolean,
        strokeCount: Int,
        action: ActionDefinition
    ): Boolean = withContext(Dispatchers.Main) {
        // If an existing session is running in this VM, cancel it first
        cancelTraining()

        val session = GestureTrainingSession(
            gestureId = gestureId,
            displayName = displayName,
            isCorePersonalization = isCore,
            requiredStrokeCount = strokeCount,
            assignedAction = action,
            suppressionManager = suppressionManager,
            templateStorage = storage
        )

        val acquired = session.start()
        if (!acquired) {
            _rejectionMessage.value = "Unable to start training: another training session is actively running or action suppression could not be reserved."
            return@withContext false
        }

        _activeSession.value = session
        _rejectionMessage.value = null

        // Collect step changes from the session
        viewModelScope.launch {
            session.currentStep.collect { step ->
                _trainingStep.value = step
            }
        }

        startMotionSegmenter(session)
        return@withContext true
    }

    private fun startMotionSegmenter(session: GestureTrainingSession) {
        val thread = HandlerThread("KinetiqStudioSensorThread").apply { start() }
        sensorThread = thread
        val handler = Handler(thread.looper)
        sensorHandler = handler

        val segmenter = MotionSegmenter(
            scope = viewModelScope,
            sensorManager = sensorManager,
            dispatcher = Dispatchers.Default
        )
        motionSegmenter = segmenter

        // Register hardware sensors
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accel?.let {
            sensorManager.registerListener(
                segmenter.accelListener,
                it,
                SensorManager.SENSOR_DELAY_FASTEST,
                handler
            )
        }

        gyro?.let {
            sensorManager.registerListener(
                segmenter.gyroListener,
                it,
                SensorManager.SENSOR_DELAY_FASTEST,
                handler
            )
        }

        // Listen for segmented motion candidates and forward to session
        viewModelScope.launch(Dispatchers.Default) {
            segmenter.candidateEvents.collect { candidate ->
                session.submitCandidate(candidate)
            }
        }
    }

    fun completeContinuousNegative(isWalking: Boolean) {
        _activeSession.value?.completeContinuousNegative(isWalking)
    }

    fun saveGestureProfile(): Boolean {
        val session = _activeSession.value ?: return false
        val saved = session.saveGestureProfile()
        if (saved) {
            stopMotionSegmenter()
            _activeSession.value = null
            loadTemplates()
        }
        return saved
    }

    fun cancelTraining() {
        _activeSession.value?.abort()
        _activeSession.value = null
        stopMotionSegmenter()
        _trainingStep.value = TrainingStep.Idle
    }

    private fun stopMotionSegmenter() {
        motionSegmenter?.let { segmenter ->
            try {
                sensorManager.unregisterListener(segmenter.accelListener)
                sensorManager.unregisterListener(segmenter.gyroListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering studio sensor listeners", e)
            }
            segmenter.reset()
        }
        motionSegmenter = null

        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
    }

    fun clearRejectionMessage() {
        _rejectionMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelTraining()
    }
}

