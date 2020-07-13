package com.meteocool.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.LocationResult
import com.meteocool.location.LocationResultHelper.Companion.getDistanceToLastLocation
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class LocationUpdatesBroadcastReceiver : BroadcastReceiver(){

    companion object {
        internal const val ACTION_PROCESS_UPDATES = "com.meteocool.backgroundlocationupdates.action" + ".PROCESS_UPDATES"
        var sendOnce = false
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if (ACTION_PROCESS_UPDATES == action) {
                val result = LocationResult.extractResult(intent)
                if (result != null) {
                    val location = result.lastLocation
                    val lastLocation = LocationResultHelper.getSavedLocationResult(context)
                    val isDistanceBiggerThan500F = getDistanceToLastLocation(location, context) > 499f
                       if(isDistanceBiggerThan500F || sendOnce){
                            Timber.i("$isDistanceBiggerThan500F")
                            Timber.i("$location is better than $lastLocation")
                            val preferences = context.defaultSharedPreferences
                           val token = preferences.getString("fb_token", "no token")
                           Timber.d(" Token $token")
                           UploadLocation().execute(location, token)
                           sendOnce = false
                        }else{

                            Timber.i("$location is not better than $lastLocation")
                        }
                    // Save the location data to SharedPreferences.
                    LocationResultHelper.saveResults(context.defaultSharedPreferences, location)
                    Timber.i(LocationResultHelper.getSavedLocationResult(context).toString())
                }
            }
        }
    }

}
