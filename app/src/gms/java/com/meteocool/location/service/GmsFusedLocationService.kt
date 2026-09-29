package com.meteocool.location.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.meteocool.permissions.PermUtils
import timber.log.Timber

/** Location through Google Play services' fused provider. */
class GmsFusedLocationService(context: Context) : ForegroundLocationService(context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
        .setMinUpdateIntervalMillis(fastestUpdateInterval)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { if (isRunning) deliver(it) }
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (isRunning || !PermUtils.isLocationPermissionGranted(context)) return
        isRunning = true
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(LocationSettingsRequest.Builder().addLocationRequest(request).build())
            .addOnFailureListener { e ->
                // Location is off; ask the user to turn it on. Updates still start and pick up once it is.
                if (e is ResolvableApiException) requestResolution(e.resolution.intentSender)
            }
        try {
            client.lastLocation.addOnSuccessListener { location -> if (location != null && isRunning) deliver(location) }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Timber.w(e)
            isRunning = false
        }
    }

    override fun stop() {
        if (!isRunning) return
        client.removeLocationUpdates(callback)
        isRunning = false
    }
}
