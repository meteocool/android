package com.meteocool.location.service

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

enum class ServiceType{
    FRONT, BACK
}

class LocationServiceFactory {

    companion object {

        fun getLocationService(context : Context, type : ServiceType) : LocationService {
             return if(isGooglePlayServicesAvailable(context)){
                 when(type){
                     ServiceType.FRONT -> FusedForegroundLocationService(context)
                     ServiceType.BACK -> FusedForegroundLocationService(context)
                 }
            }else {
                 FusedForegroundLocationService(context) //TODO Implement LocationManager
            }
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
}