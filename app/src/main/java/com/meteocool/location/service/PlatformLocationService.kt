package com.meteocool.location.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.meteocool.location.MeteocoolLocation
import com.meteocool.permissions.PermUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Location through the platform's LocationManager, for devices without
 * Google Play services (and every F-Droid build).
 */
class PlatformLocationService(context: Context) : ForegroundLocationService(context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val listener = LocationListenerCompat { location -> deliver(location) }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (isRunning || !PermUtils.isLocationPermissionGranted(context)) return
        val provider = bestProvider(context, locationManager) ?: return
        val request = LocationRequestCompat.Builder(updateInterval)
            .setMinUpdateIntervalMillis(fastestUpdateInterval)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()
        try {
            LocationManagerCompat.getCurrentLocation(
                locationManager, provider, null as CancellationSignal?, ContextCompat.getMainExecutor(context),
                Consumer<Location?> { location -> if (location != null && isRunning) deliver(location) },
            )
            LocationManagerCompat.requestLocationUpdates(
                locationManager, provider, request, ContextCompat.getMainExecutor(context), listener
            )
            isRunning = true
        } catch (e: SecurityException) {
            Timber.w(e)
        }
    }

    @SuppressLint("MissingPermission") // Removing updates needs no permission; lint cannot tell.
    override fun stop() {
        if (!isRunning) return
        LocationManagerCompat.removeUpdates(locationManager, listener)
        isRunning = false
    }

    companion object {
        /** The fused provider where the platform has one, else GPS for fine access, else the network. */
        fun bestProvider(context: Context, locationManager: LocationManager): String? {
            val enabled = locationManager.getProviders(true)
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in enabled ->
                    LocationManager.FUSED_PROVIDER
                PermUtils.isFineLocationPermissionGranted(context) && LocationManager.GPS_PROVIDER in enabled ->
                    LocationManager.GPS_PROVIDER
                LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
                LocationManager.GPS_PROVIDER in enabled -> LocationManager.GPS_PROVIDER
                else -> null
            }
        }

        /** A single current fix, or null if none arrives within 30 seconds. */
        @SuppressLint("MissingPermission")
        suspend fun currentLocation(context: Context): MeteocoolLocation? {
            if (!PermUtils.isLocationPermissionGranted(context)) return null
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = bestProvider(context, locationManager) ?: return null
            return withTimeoutOrNull(30_000) {
                suspendCancellableCoroutine { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(
                            locationManager, provider, signal, ContextCompat.getMainExecutor(context),
                            Consumer<Location?> { location -> continuation.resume(location?.let { MeteocoolLocation.from(it) }) },
                        )
                    } catch (e: SecurityException) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
