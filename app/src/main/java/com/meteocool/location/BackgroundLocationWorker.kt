package com.meteocool.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meteocool.app.app
import com.meteocool.location.service.LocationProviders
import com.meteocool.permissions.PermUtils
import java.util.concurrent.TimeUnit

/**
 * Keeps the registration's location current while the app is closed, every
 * 15 minutes, which is as often as WorkManager allows.
 */
class BackgroundLocationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.app
        if (!PermUtils.isBackgroundLocationPermissionGranted(applicationContext) || !app.registration.canRegister()) {
            return Result.success()
        }
        val fix = LocationProviders.currentLocation(applicationContext) ?: return Result.retry()
        app.prefs.saveLastLocation(fix)
        return if (app.registration.submit(fix, background = true)) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "location_updater"

        /** Scheduled only when alerts are on and the app may use location while closed. */
        fun schedule(context: Context) {
            val app = context.app
            if (!app.prefs.notification || !PermUtils.isBackgroundLocationPermissionGranted(context)) {
                cancel(context)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackgroundLocationWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
