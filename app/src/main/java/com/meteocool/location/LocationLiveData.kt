package com.meteocool.location

import android.content.Context
import android.location.Location
import androidx.lifecycle.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.meteocool.location.service.LocationServiceFactory
import com.meteocool.location.service.ServiceType
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences

class LocationLiveData(private val context : Context) : LiveData<Resource<MeteocoolLocation>?>() {

    private var locationClient = LocationServiceFactory.getLocationService(context, ServiceType.FRONT)



    override fun onActive() {
        super.onActive()
        locationClient.requestLocationUpdates()
    }

    override fun onInactive() {
        super.onInactive()
        locationClient.stopLocationUpdates()
    }

}

internal class MyLocationListener(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val callback: (Location) -> Unit
): LifecycleObserver {

    private var enabled = false

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun start() {
        if (enabled) {
            // connect
        }
    }

    fun enable() {
        enabled = true
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // connect if not connected
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        // disconnect if connected
    }
}