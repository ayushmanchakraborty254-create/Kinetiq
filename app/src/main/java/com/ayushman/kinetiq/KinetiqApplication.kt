package com.ayushman.kinetiq

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.ayushman.kinetiq.data.preferences.PreferencesRepository
import com.ayushman.kinetiq.data.sensor.SensorRepository
import com.ayushman.kinetiq.domain.studio.action.ActionExecutor
import com.ayushman.kinetiq.domain.studio.action.ActionSuppressionManager
import com.ayushman.kinetiq.domain.studio.storage.GestureTemplateStorage
import com.ayushman.kinetiq.platform.CameraLauncher
import com.ayushman.kinetiq.platform.FlashlightController
import com.ayushman.kinetiq.platform.HapticController
import com.ayushman.kinetiq.service.GestureMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KinetiqApplication : Application() {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var sensorRepository: SensorRepository
        private set

    lateinit var flashlightController: FlashlightController
        private set

    lateinit var hapticController: HapticController
        private set

    val actionSuppressionManager = ActionSuppressionManager()

    lateinit var gestureTemplateStorage: GestureTemplateStorage
        private set

    lateinit var cameraLauncher: CameraLauncher
        private set

    lateinit var actionExecutor: ActionExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(this)
        sensorRepository = SensorRepository(this)
        flashlightController = FlashlightController(this)
        hapticController = HapticController(this)
        cameraLauncher = CameraLauncher(this)
        gestureTemplateStorage = GestureTemplateStorage(this)
        actionExecutor = ActionExecutor(this, actionSuppressionManager, flashlightController, cameraLauncher, hapticController)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedCount = 0

            override fun onActivityResumed(activity: Activity) {
                resumedCount++
                isAppInForeground = resumedCount > 0
            }

            override fun onActivityPaused(activity: Activity) {
                resumedCount--
                isAppInForeground = resumedCount > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Auto-restore monitoring service if previously enabled by user
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                if (prefs.isMonitoringEnabled) {
                    GestureMonitoringService.startService(this@KinetiqApplication)
                }
            } catch (_: Exception) {}
        }
    }
}
