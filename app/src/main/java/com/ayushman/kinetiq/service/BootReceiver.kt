package com.ayushman.kinetiq.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ayushman.kinetiq.data.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefsRepo = PreferencesRepository(context.applicationContext)
                    val prefs = prefsRepo.userPreferencesFlow.first()
                    if (prefs.isMonitoringEnabled) {
                        GestureMonitoringService.startService(context.applicationContext)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

