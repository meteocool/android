package com.meteocool.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meteocool.R
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.MeteocoolActivity

/**
 * The rain-alert channel and the notifications the app shows itself. While
 * the app is in the background FCM shows alerts on its own, on the channel
 * named in the manifest.
 */
object Notifications {

    /** Set on the intent a tapped alert opens, so the backend hears about the tap. */
    const val EXTRA_FROM_NOTIFICATION = "com.meteocool.from_notification"

    /** FCM puts this on the launch intent of a notification it displayed. */
    const val EXTRA_FCM_MESSAGE_ID = "google.message_id"

    private const val ALERT_ID = 1

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

    /** Shows an alert that arrived while the app was open. */
    @SuppressLint("MissingPermission")
    fun showAlert(context: Context, title: String?, body: String?) {
        if (!PermUtils.areNotificationsEnabled(context)) return
        val intent = Intent(context, MeteocoolActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_FROM_NOTIFICATION, true)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
            .setSmallIcon(R.drawable.png_firebase_push)
            .setColor(ContextCompat.getColor(context, R.color.turquoise_cloud_0))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(ALERT_ID, notification)
    }

    /** Whether [intent] was opened by tapping an alert. */
    fun isFromNotification(intent: Intent?): Boolean =
        intent != null && (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) || intent.hasExtra(EXTRA_FCM_MESSAGE_ID))
}
