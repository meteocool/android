package com.meteocool.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.meteocool.R

/**
 * The rain-alert channel, and how the app recognises a tapped alert. Alerts
 * themselves are shown by the gms flavor (firebase/AlertNotification.kt), or by
 * FCM on its own while the app is in the background.
 */
object Notifications {

    /** Set on the intent a tapped alert opens, so the backend hears about the tap. */
    const val EXTRA_FROM_NOTIFICATION = "com.meteocool.from_notification"

    /** FCM puts this on the launch intent of a notification it displayed. */
    const val EXTRA_FCM_MESSAGE_ID = "google.message_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            context.getString(R.string.notification_channel_id),
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun clearAll(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    /** Whether [intent] was opened by tapping an alert. */
    fun isFromNotification(intent: Intent?): Boolean =
        intent != null && (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) || intent.hasExtra(EXTRA_FCM_MESSAGE_ID))
}
