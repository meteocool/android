package com.meteocool.firebase

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.notifications.AlertMute
import com.meteocool.permissions.PermUtils
import com.meteocool.widgets.WidgetData
import com.meteocool.widgets.validCoordinate

/** Fetch outside FCM's short callback lifetime; never bring back a cleared or replaced alert. */
class AlertPreviewWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @SuppressLint("MissingPermission") // Checked again after the network request.
    override suspend fun doWork(): Result {
        val origin = inputData.getString("origin") ?: return Result.success()
        val instance = inputData.getString("instance") ?: return Result.success()
        val received = inputData.getLong("received", 0)
        if (origin != MeteocoolEnvironment.currentApiBase || System.currentTimeMillis() - received !in 0..120_000) return Result.success()
        val lat = inputData.getDouble("latitude", Double.NaN)
        val lon = inputData.getDouble("longitude", Double.NaN)
        val image = WidgetData.map(applicationContext, lat, lon, 9.5, wide = false) ?: return Result.success()
        try {
            if (origin != MeteocoolEnvironment.currentApiBase || System.currentTimeMillis() - received !in 0..120_000 ||
                !PermUtils.areNotificationsEnabled(applicationContext) ||
                !applicationContext.app.prefs.notification || AlertMute.active(applicationContext.app.prefs) != null) return Result.success()
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            val current = manager.activeNotifications.firstOrNull {
                it.id == AlertNotification.ALERT_ID && it.notification.extras.getString(AlertNotification.INSTANCE) == instance
            }?.notification ?: return Result.success()
            manager.notify(AlertNotification.ALERT_ID, Notification.Builder.recoverBuilder(applicationContext, current)
                .setOnlyAlertOnce(true).setStyle(Notification.BigPictureStyle().bigPicture(image)
                    .setSummaryText(current.extras.getCharSequence(Notification.EXTRA_TEXT))).build())
        } finally { image.recycle() }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context, data: Map<String, String>, instance: String) {
            val lat = data["latitude"]?.toDoubleOrNull() ?: return
            val lon = data["longitude"]?.toDoubleOrNull() ?: return
            if (!validCoordinate(lat, lon)) return
            val work = OneTimeWorkRequestBuilder<AlertPreviewWorker>()
                .setInputData(workDataOf("latitude" to lat, "longitude" to lon, "instance" to instance,
                    "origin" to MeteocoolEnvironment.currentApiBase, "received" to System.currentTimeMillis()))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
            WorkManager.getInstance(context).enqueueUniqueWork("rain-alert-preview", ExistingWorkPolicy.REPLACE, work)
        }
    }
}
