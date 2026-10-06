package com.meteocool.firebase

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meteocool.R
import com.meteocool.notifications.Notifications
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.MeteocoolActivity

/**
 * A rain alert that arrived while the app was running but not in front (a
 * dialog of another app over it, split screen), which FCM leaves to the app
 * to show.
 */
object AlertNotification {

    private const val ALERT_ID = 1

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    fun show(context: Context, title: String?, body: String?) {
        if (!PermUtils.areNotificationsEnabled(context)) return
        val intent = Intent(context, MeteocoolActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Notifications.EXTRA_FROM_NOTIFICATION, true)
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
}
