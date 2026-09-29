package com.meteocool.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Helpers for the permissions the app works with.
 */
object PermUtils {

    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Approximate access is enough for the map and for alerts. */
    fun isLocationPermissionGranted(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun isFineLocationPermissionGranted(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** "Allow all the time": needed for alerts while the app is closed. */
    fun isBackgroundLocationPermissionGranted(context: Context): Boolean =
        isLocationPermissionGranted(context) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    /** Covers both the runtime permission (API 33+) and the app's notification switch in system settings. */
    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun needsBackgroundLocationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isBackgroundLocationPermissionGranted(context)

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
