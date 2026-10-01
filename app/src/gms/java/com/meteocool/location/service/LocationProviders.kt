package com.meteocool.location.service

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.meteocool.location.MeteocoolLocation
import com.meteocool.permissions.PermUtils
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** Fused location where Play services are installed, the platform's otherwise. */
object LocationProviders {

    private fun hasPlayServices(context: Context) =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun foreground(context: Context): ForegroundLocationService =
        if (hasPlayServices(context)) GmsFusedLocationService(context) else PlatformLocationService(context)

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): MeteocoolLocation? {
        if (!hasPlayServices(context)) return PlatformLocationService.currentLocation(context)
        if (!PermUtils.isLocationPermissionGranted(context)) return null
        val cancel = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000)
            .build()
        return try {
            withTimeoutOrNull(30_000) {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(request, cancel.token)
                    .await()
            }?.let { MeteocoolLocation.from(it) }
        } catch (e: Exception) {
            Timber.w("No current location: ${e.javaClass.simpleName}")
            null
        } finally {
            cancel.cancel()
        }
    }
}
