package com.meteocool.location

import Resource
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.LiveData
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import timber.log.Timber

class LocationLiveData(private val context : Context) : LiveData<Resource<LocationModel>>(){

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override fun onInactive() {
        super.onInactive()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    @SuppressLint("MissingPermission")
    override fun onActive() {
        super.onActive()
        Timber.d("Called")
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                Timber.d("$location")
                location?.also {
                    setLocationData(it)
                }
            }
            .addOnFailureListener {
                Timber.e(it)
            }
        startLocationUpdates()
    }
    fun test(){
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val builder =
            LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            Timber.d("Starting location updates")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                value = Resource(exception)
                Timber.d("Created Resource")
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            for (location in locationResult.locations) {
                setLocationData(location)
            }
        }
    }

    private fun setLocationData(location: Location) {
        value = Resource(LocationModel(
            longitude = location.longitude,
            latitude = location.latitude,
            altitude = location.altitude,
            accuracy = location.accuracy
        ))
    }

    companion object {
        val locationRequest: LocationRequest = LocationRequest.create().apply {
            interval = 0
            fastestInterval = 0
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
}