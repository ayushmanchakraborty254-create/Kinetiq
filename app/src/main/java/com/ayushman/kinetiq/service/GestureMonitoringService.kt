package com.ayushman.kinetiq.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.ayushman.kinetiq.KinetiqApplication
import com.ayushman.kinetiq.data.preferences.PreferencesRepository
import com.ayushman.kinetiq.data.sensor.SensorRepository
import com.ayushman.kinetiq.domain.gesture.GestureEngine
import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureType
import com.ayushman.kinetiq.domain.model.UserPreferences
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.platform.CameraLauncher
import com.ayushman.kinetiq.platform.FlashlightController
import com.ayushman.kinetiq.platform.HapticController
import com.ayushman.kinetiq.platform.PocketProtectionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GestureMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var sensorRepository: SensorRepository
    private lateinit var gestureEngine: GestureEngine
    private lateinit var notificationManager: MonitoringNotificationManager
    private lateinit var flashlightController: FlashlightController
    private lateinit var cameraLauncher: CameraLauncher
    private lateinit var hapticController: HapticController
    private lateinit var pocketController: PocketProtectionController

    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var sensorJobs = mutableListOf<Job>()
    private var isPaused: Boolean = false
    private var currentPreferences: UserPreferences = UserPreferences()

    companion object {
        const val ACTION_START = "com.ayushman.kinetiq.ACTION_START"
        const val ACTION_STOP = "com.ayushman.kinetiq.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, GestureMonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GestureMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        preferencesRepository = PreferencesRepository(applicationContext)
        sensorRepository = SensorRepository(applicationContext)

        val app = application as? KinetiqApplication
        gestureEngine = GestureEngine(
            scope = serviceScope,
            templateStorage = app?.gestureTemplateStorage,
            actionExecutor = app?.actionExecutor,
            suppressionManager = app?.actionSuppressionManager
        )
        notificationManager = MonitoringNotificationManager(applicationContext)
        flashlightController = FlashlightController(applicationContext)
        cameraLauncher = CameraLauncher(applicationContext)
        hapticController = HapticController(applicationContext)
        pocketController = PocketProtectionController()

        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager

        startForegroundWithNotification()
        observePreferences()
        observeGestureEvents()
    }

    private fun startForegroundWithNotification() {
        val notification = notificationManager.buildNotification(
            isPaused = isPaused,
            activeGesturesDescription = getGesturesSummary()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                MonitoringNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(MonitoringNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoringAndSelf()
                return START_NOT_STICKY
            }
            MonitoringNotificationManager.ACTION_PAUSE -> {
                pauseMonitoring()
            }
            MonitoringNotificationManager.ACTION_RESUME -> {
                resumeMonitoring()
            }
            ACTION_START -> {
                if (isPaused) {
                    resumeMonitoring()
                }
            }
        }

        return START_STICKY
    }

    private fun observePreferences() {
        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collectLatest { prefs ->
                currentPreferences = prefs
                gestureEngine.updatePreferences(prefs)

                if (!prefs.isMonitoringEnabled) {
                    stopMonitoringAndSelf()
                    return@collectLatest
                }

                updateWakeLock(prefs.isScreenOffMonitoringEnabled)

                if (!isPaused) {
                    restartSensorCollection()
                }
                notificationManager.updateNotification(isPaused, getGesturesSummary())
            }
        }
    }

    private fun observeGestureEvents() {
        val app = application as? KinetiqApplication
        val actionExec = app?.actionExecutor

        serviceScope.launch {
            gestureEngine.gestureEvents.collect { event ->
                if (pocketController.isGestureBlocked()) return@collect

                // If this is a personalized gesture, GestureEngine already dispatched it through ActionExecutor
                if (event.isPersonalized) {
                    return@collect
                }

                when (event.type) {
                    GestureType.DOUBLE_CHOP -> {
                        val timestampMs = event.timestampNs / 1_000_000L
                        if (actionExec != null) {
                            actionExec.executeAction(timestampMs, ActionDefinition.ToggleFlashlight())
                        } else {
                            val toggled = flashlightController.toggleFlashlight()
                            if (toggled && currentPreferences.isHapticEnabled) {
                                hapticController.performGestureConfirmation()
                            }
                        }
                    }

                    GestureType.DOUBLE_TWIST -> {
                        val timestampMs = event.timestampNs / 1_000_000L
                        if (actionExec != null) {
                            actionExec.executeAction(timestampMs, ActionDefinition.LaunchCamera())
                        } else {
                            cameraLauncher.launchCamera()
                            if (currentPreferences.isHapticEnabled) {
                                hapticController.performDoublePulse()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun restartSensorCollection() {
        sensorJobs.forEach { it.cancel() }
        sensorJobs.clear()

        pocketController.onServiceStarted()

        val app = application as? KinetiqApplication
        val hasActiveTemplates = app?.gestureTemplateStorage?.loadAllTemplates()?.any { it.isEnabled } == true

        // 1. Linear acceleration for chop (or personalized/custom gestures)
        if (currentPreferences.isChopEnabled || hasActiveTemplates) {
            val chopJob = serviceScope.launch {
                sensorRepository.linearAccelerationFlow().collect { reading ->
                    gestureEngine.onLinearAccelerationReading(reading)
                }
            }
            sensorJobs.add(chopJob)
        }

        // 2. Gyroscope for twist (or personalized/custom gestures)
        if (currentPreferences.isTwistEnabled || hasActiveTemplates) {
            val twistJob = serviceScope.launch {
                sensorRepository.gyroscopeFlow().collect { reading ->
                    gestureEngine.onGyroscopeReading(reading)
                }
            }
            sensorJobs.add(twistJob)
        }

        // 3. Proximity sensor for pocket protection
        if (currentPreferences.isPocketProtectionEnabled) {
            val proxJob = serviceScope.launch {
                sensorRepository.proximityFlow().collect { reading ->
                    pocketController.onProximityChanged(reading.isNear)
                    gestureEngine.onProximityReading(reading)
                }
            }
            sensorJobs.add(proxJob)
        }
    }

    private fun pauseMonitoring() {
        isPaused = true
        sensorJobs.forEach { it.cancel() }
        sensorJobs.clear()
        gestureEngine.reset()
        releaseWakeLock()
        notificationManager.updateNotification(isPaused = true, getGesturesSummary())
    }

    private fun resumeMonitoring() {
        isPaused = false
        updateWakeLock(currentPreferences.isScreenOffMonitoringEnabled)
        restartSensorCollection()
        notificationManager.updateNotification(isPaused = false, getGesturesSummary())
    }

    private fun updateWakeLock(screenOffEnabled: Boolean) {
        if (screenOffEnabled) {
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "kinetiq:motion_sensor_wakelock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                // Safety timeout of 4 hours to prevent runaway battery drain
                wakeLock?.acquire(4 * 60 * 60 * 1000L)
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun getGesturesSummary(): String {
        val list = mutableListOf<String>()
        if (currentPreferences.isChopEnabled) list.add("Chop")
        if (currentPreferences.isTwistEnabled) list.add("Twist")
        return if (list.isEmpty()) "None enabled" else list.joinToString(" + ")
    }

    private fun stopMonitoringAndSelf() {
        sensorJobs.forEach { it.cancel() }
        sensorJobs.clear()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        sensorJobs.forEach { it.cancel() }
        sensorJobs.clear()
        releaseWakeLock()
        flashlightController.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

