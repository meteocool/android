package com.meteocool.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.LocationResult
import com.meteocool.location.service.LocationServiceFactory
import com.meteocool.location.service.ServiceType
import com.meteocool.preferences.SharedPrefUtils
import com.vmadalin.easypermissions.EasyPermissions
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

/**
 * Receives locations updates and performs an upload if new location is better than the old one.
 * Receives phone reboots and reregisters location updates if user preferences and app permissions are met.
 */
class LocationUpdatesBroadcastReceiver : BroadcastReceiver(){

    companion object {
        internal const val ACTION_PROCESS_UPDATES = "com.meteocool.backgroundlocationupdates.action" + ".PROCESS_UPDATES"
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent?) {
        Timber.d("OnReceive $intent")
        if (intent != null) {
            Timber.d("OnReceive action ${intent.action}")
            val action = intent.action
            if (ACTION_PROCESS_UPDATES == action) {
                val result = LocationResult.extractResult(intent)
                Timber.d("Location: $result")
                if (result != null) {
                    val location = result.lastLocation
                    val preferences = context.defaultSharedPreferences
                    val lastLocation = SharedPrefUtils.getSavedLocationResult(preferences)
                    val isDistanceBiggerThan500F = getDistanceToLastLocation(location, context) > 499f
                       if(isDistanceBiggerThan500F){
                            Timber.i("$isDistanceBiggerThan500F")
                            Timber.i("$location is better than $lastLocation")

                           val token = SharedPrefUtils.getFirebaseToken(preferences)
                           Timber.d(" Token $token")
                           UploadLocation().execute(location, token, preferences)
                        }else{
                            Timber.i("$location is not better than $lastLocation")
                        }
                    // Save the location data to SharedPreferences.
                    SharedPrefUtils.saveResults(context.defaultSharedPreferences, location)
                    Timber.i("$lastLocation")
                }
            }
            else if(Intent.ACTION_BOOT_COMPLETED == action){
                if(EasyPermissions.hasPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    LocationServiceFactory.getLocationService(context, ServiceType.BACK).requestLocationUpdates()
                }
            }
        }
    }

    private fun getDistanceToLastLocation(newLocation: Location, context: Context): Float {
        val distance = FloatArray(1)
        Location.distanceBetween(
            newLocation.latitude,
            newLocation.longitude,
            context.defaultSharedPreferences.getFloat(
                SharedPrefUtils.KEY_LOCATION_LAT, -1.0f
            ).toDouble(),
            context.defaultSharedPreferences.getFloat(
                SharedPrefUtils.KEY_LOCATION_LON, -1.0f
            ).toDouble(),
            distance
        )
        Timber.d("Calculated distance: ${distance[0]}")
        return distance[0]
    }
}
