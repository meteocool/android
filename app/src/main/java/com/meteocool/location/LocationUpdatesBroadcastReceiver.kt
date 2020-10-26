package com.meteocool.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.meteocool.MeteocoolActivity
import com.meteocool.security.Validator
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

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
                    val lastLocation = LocationUtils.getSavedLocationResult(context)
                    val isDistanceBiggerThan500F = LocationUtils.getDistanceToLastLocation(location, context) > 499f
                       if(isDistanceBiggerThan500F){
                            Timber.i("$isDistanceBiggerThan500F")
                            Timber.i("$location is better than $lastLocation")
                            val preferences = context.defaultSharedPreferences
                           val token = preferences.getString("fb_token", "no token")
                           Timber.d(" Token $token")
                           UploadLocation().execute(location, token)
                        }else{

                            Timber.i("$location is not better than $lastLocation")
                        }
                    // Save the location data to SharedPreferences.
                    LocationUtils.saveResults(context.defaultSharedPreferences, location)
                    Timber.i(LocationUtils.getSavedLocationResult(context).toString())
                }
            }
            else if(Intent.ACTION_BOOT_COMPLETED == action){
                val fused = LocationServices.getFusedLocationProviderClient(context)
                if(Validator.isBackgroundLocationPermissionGranted(context)) {
                    fused.requestLocationUpdates(
                        backgroundLocationRequest,
                        getPendingIntent(context)
                    )
                }
            }
        }
    }


    private fun getPendingIntent(context : Context) : PendingIntent{
        val intent = Intent(context, LocationUpdatesBroadcastReceiver::class.java)
        intent.action = LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val backgroundLocationRequest: LocationRequest
        get() {
            return LocationRequest.create().apply {
                interval = MeteocoolActivity.UPDATE_INTERVAL
                fastestInterval = MeteocoolActivity.FASTEST_UPDATE_INTERVAL
                priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                maxWaitTime = MeteocoolActivity.MAX_WAIT_TIME
            }
        }

}
