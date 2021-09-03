package com.meteocool.location

import android.content.Context
import android.location.Location
import android.os.Bundle
import androidx.work.WorkManager
import com.meteocool.location.storage.LocationPersistenceWorker
import com.yayandroid.locationmanager.listener.LocationListener

class LocationListener(val context: Context) : LocationListener {
    override fun onProcessTypeChanged(processType: Int) {
        return
    }

    override fun onLocationChanged(location: Location?) {
        if (location !== null) {
            handleLocation(location)
        }
    }

    override fun onLocationFailed(type: Int) {
        return
    }

    override fun onPermissionGranted(alreadyHadPermission: Boolean) {
        return
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        return
    }

    override fun onProviderEnabled(provider: String?) {
        return
    }

    override fun onProviderDisabled(provider: String?) {
        return
    }

    /**
     * Persist location to database and upload it to the server.
     * @param location to persist and upload
     */
    private fun handleLocation(
        location: Location,
    ) {
        WorkManager.getInstance(context)
            .enqueue(
                listOf(
                    LocationPersistenceWorker.createRequest(
                        LocationPersistenceWorker.createMeteocooLocationData(location)
                    ),
                )
            )
    }
}