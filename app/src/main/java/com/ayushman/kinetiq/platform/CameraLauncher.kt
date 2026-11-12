package com.ayushman.kinetiq.platform

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ayushman.kinetiq.KinetiqApplication
import com.ayushman.kinetiq.R

class CameraLauncher(private val context: Context) {

    companion object {
        private const val TAG = "KinetiqCamera"
        const val ALERT_CHANNEL_ID = "kinetiq_alerts_channel"
        const val CAMERA_ALERT_NOTIFICATION_ID = 2001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createAlertNotificationChannel()
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.notification_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_alert_channel_desc)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Resolves and launches the camera application directly.
     * Complies with Android Background Activity Launch (BAL) platform policies:
     * - Uses STILL_IMAGE_CAMERA_SECURE if screen is locked (Keyguard bypass).
     * - If in foreground or if SYSTEM_ALERT_WINDOW (canDrawOverlays) is granted: launches camera directly.
     * - If in background without overlay permission: posts a high-priority Heads-Up Notification fallback.
     */
    fun launchCamera(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true

        val action = if (isLocked) {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
        } else {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }

        var cameraIntent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }

        val packageManager = context.packageManager
        var resolvedActivities = packageManager.queryIntentActivities(cameraIntent, 0)

        // Fallback to non-secure intent if secure intent is not handled by OEM camera
        if (resolvedActivities.isEmpty() && isLocked) {
            cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            resolvedActivities = packageManager.queryIntentActivities(cameraIntent, 0)
        }

        if (resolvedActivities.isEmpty()) {
            Log.w(TAG, "No camera application installed to handle camera intents")
            return false
        }

        val inForeground = KinetiqApplication.isAppInForeground
        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        Log.d(TAG, "Executing camera trigger: inForeground=$inForeground, canDrawOverlays=$canDrawOverlays, isLocked=$isLocked")

        // Direct launch condition: either app has visible window OR user granted overlay permission (BAL exemption)
        return if (inForeground || canDrawOverlays) {
            try {
                context.startActivity(cameraIntent)
                Log.i(TAG, "Camera activity launched directly (BAL permitted)")
                // Dismiss any leftover notification
                notificationManager.cancel(CAMERA_ALERT_NOTIFICATION_ID)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Direct camera launch failed unexpectedly, falling back to notification", e)
                postCameraLaunchNotification(cameraIntent, isLocked)
                false
            }
        } else {
            // Background execution without overlay permission: Android BAL blocks direct startActivity.
            // Post high-priority notification with full-screen intent fallback.
            Log.w(TAG, "Direct launch blocked by Android BAL: SYSTEM_ALERT_WINDOW not granted. Posting notification.")
            postCameraLaunchNotification(cameraIntent, isLocked)
            false
        }
    }

    private fun postCameraLaunchNotification(cameraIntent: Intent, isLocked: Boolean) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            CAMERA_ALERT_NOTIFICATION_ID,
            cameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_twist)
            .setContentTitle("Camera Ready")
            .setContentText("Double twist detected. Tap to open camera.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, isLocked)
            .addAction(
                R.drawable.ic_twist,
                "Open Camera",
                pendingIntent
            )
            .build()

        notificationManager.notify(CAMERA_ALERT_NOTIFICATION_ID, notification)
        Log.d(TAG, "Posted camera notification fallback")
    }
}
