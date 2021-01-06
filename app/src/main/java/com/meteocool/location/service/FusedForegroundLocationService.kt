package com.meteocool.location.service

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.meteocool.location.MeteocoolLocation
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber
import java.util.concurrent.TimeUnit

class FusedForegroundLocationService(context: Context) : LocationService(context) {

    /**
     * The entry point to Google Play Services.
     */
    private val mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context)


    private var locationCallback: LocationCallback

    init{
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                Timber.d("new location $locationResult")
                for (location in locationResult.locations){
                    getBetterLocation(location)
                }
            }
        }
    }

    private val locationRequest: LocationRequest
        get() {
            return LocationRequest.create().apply {
                interval = super.updateInterval
                fastestInterval = super.fastestUpdateInterval
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                maxWaitTime = super.maxWaitTime
            }
        }

    override fun requestLocationUpdates() {
        Timber.d("Request Updates")
        super.updateInterval = TimeUnit.SECONDS.toMillis(20)
        super.fastestUpdateInterval = TimeUnit.SECONDS.toMillis(10)
        super.maxWaitTime = TimeUnit.SECONDS.toMillis(20)
        try {
            val builder =
                LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
            val client: SettingsClient = LocationServices.getSettingsClient(context)
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
            task.addOnSuccessListener {
                val looper = Looper.getMainLooper()
                Timber.d("Starting location updates $looper")
                mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, looper)
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
        mFusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun getBetterLocation(location : Location?) : MeteocoolLocation {
        var result: MeteocoolLocation? = null
        result = SharedPrefUtils.getSavedLocationResult(context.defaultSharedPreferences)
        if (location != null) {
            val lastLocation = MeteocoolLocation(
                location.latitude,
                location.longitude,
                location.altitude,
                location.accuracy,
                location.elapsedRealtimeNanos
            )
            if (lastLocation > result) {
                result = lastLocation
                SharedPrefUtils.saveResults(context.defaultSharedPreferences, location)
            }
        }
        return result
    }


}