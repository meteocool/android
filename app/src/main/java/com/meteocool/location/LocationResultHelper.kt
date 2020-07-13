package com.meteocool.location;


import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

/**
 * Class to process location results.
 */
internal class LocationResultHelper() {

    companion object {
        const val KEY_LOCATION_UPDATES_RESULT_LAT = "location-update-result-latitude"
        const val KEY_LOCATION_UPDATES_RESULT_LON = "location-update-result-longitude"
        const val KEY_LOCATION_UPDATES_RESULT_ALT = "location-update-result-altitude"
        const val KEY_LOCATION_UPDATES_RESULT_ACC = "location-update-result-accuracy"

        var NOTIFICATION_TIME = 15
        var NOTIFICATION_INTENSITY = 10

        /**
         * Saves location result as a string to [android.content.SharedPreferences].
         */
        fun saveResults(sharedPreferences : SharedPreferences, mLocation : Location ) {
            sharedPreferences
                .edit()
                .putFloat(KEY_LOCATION_UPDATES_RESULT_LAT, mLocation.latitude.toFloat())
                .putFloat(KEY_LOCATION_UPDATES_RESULT_LON,  mLocation.longitude.toFloat())
                .putFloat(KEY_LOCATION_UPDATES_RESULT_ALT,  mLocation.altitude.toFloat())
                .putFloat(KEY_LOCATION_UPDATES_RESULT_ACC, mLocation.accuracy)
                .apply()
        }

        /**
         * Fetches location results from [android.content.SharedPreferences].
         */
        fun getSavedLocationResult(context: Context): Map<String, Float> {
            return mapOf(
                Pair(KEY_LOCATION_UPDATES_RESULT_LAT, context.defaultSharedPreferences.getFloat(KEY_LOCATION_UPDATES_RESULT_LAT, -1.0f)),
                Pair(KEY_LOCATION_UPDATES_RESULT_LON, context.defaultSharedPreferences.getFloat(KEY_LOCATION_UPDATES_RESULT_LON, -1.0f)),
                Pair(KEY_LOCATION_UPDATES_RESULT_ALT, context.defaultSharedPreferences.getFloat(KEY_LOCATION_UPDATES_RESULT_ALT, -1.0f)),
                Pair(KEY_LOCATION_UPDATES_RESULT_ACC, context.defaultSharedPreferences.getFloat(KEY_LOCATION_UPDATES_RESULT_ACC, -1.0f)))
        }

        fun getDistanceToLastLocation(newLocation : Location, context: Context) : Float{
            val distance = FloatArray(1)
            Location.distanceBetween(newLocation.latitude, newLocation.longitude, getSavedLocationResult(context).getValue(KEY_LOCATION_UPDATES_RESULT_LAT).toDouble(), getSavedLocationResult(context).getValue(KEY_LOCATION_UPDATES_RESULT_LON).toDouble() , distance)
            Timber.d("Calculated distance: ${distance[0]}")
            return distance[0]
        }
    }
}
