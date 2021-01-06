package com.meteocool.location

import android.content.Context
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient


class LocationServiceFactory : GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    companion object {
        private var INSTANCE: LocationService? = null
        fun getLocationService(context : Context) : LocationService?{
             if(isGooglePlayServicesAvailable(context)){
                INSTANCE = FusedLocationService.getService(context)
            }else if(INSTANCE == null && !isGooglePlayServicesAvailable(context)){
                FusedLocationService(context) //TODO Implement LocationManager
            }
            return INSTANCE
        }

        private fun isGooglePlayServicesAvailable(context : Context): Boolean {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val status = googleApiAvailability.isGooglePlayServicesAvailable(context)
            if (status != ConnectionResult.SUCCESS) {
                return false
            }
            return true
        }
    }

    //TODO Not sure if this works. If GooglePlayServices disconnect or otherwise, switch to Locationmanager?
    override fun onConnected(p0: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onConnectionSuspended(p0: Int) {
        TODO("Not yet implemented")
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        TODO("Not yet implemented")
    }

}