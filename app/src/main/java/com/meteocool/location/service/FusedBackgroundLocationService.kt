package com.meteocool.location.service

import android.content.Context
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class FusedBackgroundLocationService(context: Context) : LocationService(context) {

    /**
     * The entry point to Google Play Services.
     */
    private val mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest: LocationRequest
        get() {
            return LocationRequest.create().apply {
                interval = super.updateInterval
                fastestInterval = super.fastestUpdateInterval
                priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                maxWaitTime = super.maxWaitTime
            }
        }

    override fun requestLocationUpdates() {
        try {
            val builder =
                LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
            val client: SettingsClient = LocationServices.getSettingsClient(context)
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
            task.addOnSuccessListener {
                Timber.d("Starting background location updates")
                mFusedLocationClient.requestLocationUpdates(
                    locationRequest, pendingIntent
                )
            }
            task.addOnFailureListener {
                Timber.e(it)
                context.defaultSharedPreferences.edit()
                    .putBoolean("permissionEnabled", false)
                    .apply()
            }
        } catch (e: SecurityException) {
            Timber.e(e)
        }
    }

    override fun stopLocationUpdates() {
        Timber.d("Stopped")
        mFusedLocationClient.removeLocationUpdates(pendingIntent)
    }


}