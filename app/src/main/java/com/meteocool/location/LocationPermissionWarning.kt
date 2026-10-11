package com.meteocool.location

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.permissions.PermUtils

/**
 * Tells the user when rain alerts no longer follow them, as the iOS app's
 * LocationPermissionWarning does.
 *
 * Android takes "Allow all the time" back on its own: its reminders that an
 * app uses location in the background offer to limit it, and it resets the
 * permissions of apps left unused. The app is not told. With location only
 * while open, alerts keep going to wherever it was last opened, and the
 * background worker stops posting. The app sees it the next time it runs,
 * on opening or in the background worker, and says so once per downgrade:
 * an alert in the app, or a notification from the worker.
 *
 * Only a real downgrade counts: someone who never chose "Allow all the time"
 * was already told what it does when alerts were turned on.
 */
object LocationPermissionWarning {

    private const val NOTIFICATION_ID = 2

    /** What `post_location` reports as `locationPermission`. */
    fun reported(context: Context): String = when {
        PermUtils.isBackgroundLocationPermissionGranted(context) -> "always"
        PermUtils.isLocationPermissionGranted(context) -> "whenInUse"
        else -> "denied"
    }

    /**
     * Once per downgrade. "Allow all the time" is noted whenever it is seen,
     * and the note is used up by the warning, so only a grant after it arms
     * the next one.
     */
    private fun shouldWarn(context: Context): Boolean {
        val prefs = context.app.prefs
        if (PermUtils.isBackgroundLocationPermissionGranted(context)) {
            prefs.alwaysLocationSeen = true
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return false
        }
        val applies = prefs.notification && PermUtils.isLocationPermissionGranted(context)
        if (!applies || !prefs.alwaysLocationSeen) return false
        prefs.alwaysLocationSeen = false
        return true
    }

    /** On opening: an alert, with the way to Settings. Returns it, or null. */
    fun showIfNeeded(activity: Activity): Dialog? {
        if (!shouldWarn(activity)) return null
        return MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.location_permission_downgraded_title)
            .setMessage(R.string.location_permission_downgraded_body)
            .setPositiveButton(R.string.change_in_settings) { _, _ ->
                activity.startActivity(PermUtils.appSettingsIntent(activity))
            }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    /** In the background worker: a notification that opens the app's settings. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    fun notifyIfNeeded(context: Context) {
        if (!PermUtils.areNotificationsEnabled(context) || !shouldWarn(context)) return
        val pending = PendingIntent.getActivity(
            context, NOTIFICATION_ID, PermUtils.appSettingsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.getString(R.string.location_permission_downgraded_body)
        val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
            .setSmallIcon(R.drawable.png_firebase_push)
            .setColor(ContextCompat.getColor(context, R.color.turquoise_cloud_0))
            .setContentTitle(context.getString(R.string.location_permission_downgraded_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
