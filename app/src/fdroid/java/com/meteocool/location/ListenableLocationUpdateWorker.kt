package com.meteocool.location

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.*
import com.google.common.util.concurrent.ListenableFuture
import com.meteocool.location.storage.LocationPersistenceWorker
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class ListenableLocationUpdateWorker(private val context: Context, params: WorkerParameters) :
    ListenableWorker(context, params) {


    private val locationManager: LocationManager =
        applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val criteria: Criteria = Criteria()

    init {
        criteria.accuracy = Criteria.ACCURACY_FINE
        criteria.powerRequirement = Criteria.POWER_LOW
    }


    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture {
            try {
//                val savedLocation =
//                    SharedPrefUtils.getSavedLocationResult(context.defaultSharedPreferences)
//                val listener = object : MyLocationListener() {
//                    override fun onLocationChanged(location: Location) {
//                        Timber.d("Old: $savedLocation")
//                        Timber.d("New: $location")
//                        Timber.d(
//                            "Called ${
//                                isLocationBetter(
//                                    location,
//                                    savedLocation
//                                )
//                            }"
//                        )
//                        super.onLocationChanged(location)
//
//                        if (isLocationBetter(location, savedLocation)) {
//                            handleLocation(location, it)
//                        }
//                        locationManager.removeUpdates(this)
//                        it.set(Result.success())
//                    }
//
//                    override fun onProviderDisabled(provider: String) {
//                        super.onProviderDisabled(provider)
//                        it.set(Result.failure())
//                    }
//                }
//                val prov = locationManager.getBestProvider(criteria, true) // Test with GPS
//                Timber.d("$prov")
//                if (prov == null) {
//                    it.set(Result.failure())
//                } else {
////                    val location =
////                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//
//                    locationManager.requestLocationUpdates(
//                        prov,
//                        1000,
//                        500f,
//                        listener
//                    )
//                }
            } catch (e: SecurityException) {
                it.set(Result.failure())
            }
        }
    }

    /**
     * Persist location to database and upload it to the server.
     * @param location to persist and upload
     * @param it result for the worker
     */
    private fun handleLocation(
        location: Location,
        it: CallbackToFutureAdapter.Completer<Result>
    ) {
        WorkManager.getInstance(applicationContext)
            .enqueue(
                listOf(
                    LocationPersistenceWorker.createRequest(
                        LocationPersistenceWorker.createMeteocooLocationData(location)
                    ),
                    UploadWorker.createRequest(
                        UploadWorker.createDataForLocationPost(
                            context.defaultSharedPreferences,
                            MeteocoolLocationFactory.new(location)
                        )
                    )
                )
            )
        it.set(Result.success())
    }

    private fun isDistanceGreaterThan500F(
        newLocation: Location,
        oldLocation: MeteocoolLocation
    ): Boolean {
        val distance = FloatArray(1)
        Location.distanceBetween(
            newLocation.latitude,
            newLocation.longitude,
            oldLocation.latitude,
            oldLocation.longitude,
            distance
        )
        Timber.d("Distance: ${distance[0]}")
        return distance[0] > 499f
    }
}
