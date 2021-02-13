package com.meteocool.location.service

import com.meteocool.location.Resource
import android.content.Context
import android.content.IntentSender
import android.location.GnssStatus
import android.location.Location
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.UploadLocation
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber
import java.util.concurrent.TimeUnit

class FusedForegroundLocationService(context: Context) : LocationService(context) {

    /**
     * The entry point to Google Play Services.
     */
    private val mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context)


    private var resultAsLiveData : MutableLiveData<Resource<MeteocoolLocation>> = MutableLiveData(super.liveData().value)

    private var requesting : Boolean = false

    private var locationCallback: LocationCallback

    init{
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                Timber.d("new location $locationResult")
                for (location in locationResult.locations){
                    updateLocationIfBetter(location)
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
                val task: Task<LocationSettingsResponse> =
                    client.checkLocationSettings(builder.build())
                task.addOnSuccessListener {
                    mFusedLocationClient.lastLocation
                        .addOnSuccessListener { location : Location? ->
                            updateLocationIfBetter(location)
                        }
                    val looper = Looper.getMainLooper()
//                Timber.d("Starting location updates $looper")
                    mFusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        looper
                    )
                }
                task.addOnFailureListener {
                    if (it is ResolvableApiException) {
                        try {
                            Timber.e(it)
                            resultAsLiveData.value = Resource(it)
                        } catch (sendEx: IntentSender.SendIntentException) {
                            // Ignore the error.
                        }
                    }
                }
            } catch (e: SecurityException) {
                Timber.e(e)
            }
    }

    override fun stopLocationUpdates() {
        Timber.d("Stopped")
        mFusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateLocationIfBetter(location : Location?){
        val preferences = context.defaultSharedPreferences
        val lastLocation = SharedPrefUtils.getSavedLocationResult(preferences)
        if (location != null) {
            val currentLocation = MeteocoolLocation(
                location.latitude,
                location.longitude,
                location.altitude,
                location.accuracy,
                location.elapsedRealtimeNanos
            )
            if (currentLocation > lastLocation) {
                Timber.d("Update location to $currentLocation")
                resultAsLiveData.value = Resource(currentLocation)
                SharedPrefUtils.saveResults(preferences, location)
                UploadLocation().execute(location, SharedPrefUtils.getFirebaseToken(preferences), preferences)
            }
        }
    }

    override fun liveData() = resultAsLiveData
}