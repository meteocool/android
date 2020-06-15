package com.meteocool.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.util.Log
import com.google.android.gms.location.LocationResult
import com.meteocool.location.LocationResultHelper.Companion.getDistanceToLastLocation
import org.jetbrains.anko.defaultSharedPreferences

class LocationUpdatesBroadcastReceiver : BroadcastReceiver(){

    companion object {
        private const val TAG = "LUBroadcastReceiver"
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
                            Log.i(TAG, "$isDistanceBiggerThan500F")
                            Log.i(TAG, "$location is better than $lastLocation")
                            val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)
                           val token = preferenceManager.getString("fb_token", "no token")
                           Log.d(TAG, " Token $token")
                           UploadLocation().execute(location, token)
                           sendOnce = false
                        }else{

                            Log.i(TAG, "$location is not better than $lastLocation")
                        }
                    // Save the location data to SharedPreferences.
                    LocationResultHelper.saveResults(context.defaultSharedPreferences, location)
                    Log.i(TAG, LocationResultHelper.getSavedLocationResult(context).toString())
                }
            }
        }
    }

}
