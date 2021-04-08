package com.meteocool.location.service

import com.meteocool.location.Resource
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.MeteocoolLocationFactory
import com.meteocool.location.storage.LocationPersistenceWorker
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class FusedLocationService(context: Context) : ForegroundLocationService(context) {

    /**
     * The entry point to Google Play Services.
     */
    private val mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context)


    private var resultAsLiveData: MutableLiveData<Resource<MeteocoolLocation>> =
        MutableLiveData(Resource(SharedPrefUtils.getSavedLocationResult(context.defaultSharedPreferences)))


    private var locationCallback: LocationCallback
    private lateinit var job: Job

    init {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
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
        try {
            val builder =
                LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
            val client: SettingsClient = LocationServices.getSettingsClient(context)
            val task: Task<LocationSettingsResponse> =
                client.checkLocationSettings(builder.build())
            task.addOnSuccessListener {
                mFusedLocationClient.getCurrentLocation(
                    LocationRequest.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { location: Location? ->
                    updateLocationIfBetter(location)
                }
                val test = Looper.getMainLooper()
                job = CoroutineScope(Dispatchers.Default).launch {
                    val looper = Looper.getMainLooper()
                    Timber.d("Starting location updates $looper $test")
                    mFusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        looper
                    )
                }
                Timber.d("Job started: ${job.start()}")
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
        if (this::job.isInitialized) {
            job.cancel()
        }
    }

    override fun liveData() = resultAsLiveData

    private fun updateLocationIfBetter(location: Location?) {
        val preferences = context.defaultSharedPreferences
        val lastLocation = SharedPrefUtils.getSavedLocationResult(preferences)
        if (location != null) {
            val currentLocation = MeteocoolLocationFactory.new(location)
            val distance = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                lastLocation.latitude,
                lastLocation.longitude,
                distance
            )
            Timber.d("Distance ${distance[0]}")
            if (distance[0] > 499f) {
                Timber.d("Update location to $location")
                if (preferences.getBoolean("notification", false)) {
                    val uploadLocation = UploadWorker.createRequest(
                        UploadWorker.createDataForLocationPost(
                            preferences,
                            currentLocation
                        )
                    )
                    WorkManager.getInstance(context)
                        .enqueue(listOf(uploadLocation))
                        .result
                }
            }
            resultAsLiveData.value = Resource(currentLocation)
            val persistLocation = LocationPersistenceWorker.createRequest(
                LocationPersistenceWorker.createMeteocooLocationData(location)
            )
            WorkManager.getInstance(context)
                .enqueue(listOf(persistLocation))
                .result
        }
    }

}