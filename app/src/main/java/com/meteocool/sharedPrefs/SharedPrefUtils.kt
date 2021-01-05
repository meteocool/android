package com.meteocool.sharedPrefs

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

/**
 * Helper
 */
class SharedPrefUtils {
    companion object {
        const val KEY_LOCATION_UPDATES_RESULT_LAT = "location_latitude"
        const val KEY_LOCATION_UPDATES_RESULT_LON = "location_longitude"
        const val KEY_LOCATION_UPDATES_RESULT_ALT = "location_altitude"
        const val KEY_LOCATION_UPDATES_RESULT_ACC = "location_accuracy"
        var NOTIFICATION_TIME = 15
        var NOTIFICATION_INTENSITY = 10

        /**
         * Saves location result as a string to [android.content.SharedPreferences].
         */
        fun saveResults(sharedPreferences: SharedPreferences, mLocation: Location) {
            sharedPreferences
                .edit()
                .putFloat(KEY_LOCATION_UPDATES_RESULT_LAT, mLocation.latitude.toFloat())
                .putFloat(KEY_LOCATION_UPDATES_RESULT_LON, mLocation.longitude.toFloat())
                .putFloat(KEY_LOCATION_UPDATES_RESULT_ALT, mLocation.altitude.toFloat())
                .putFloat(KEY_LOCATION_UPDATES_RESULT_ACC, mLocation.accuracy)
                .apply()
        }

        /**
         * Fetches location results from [android.content.SharedPreferences].
         */
        fun getSavedLocationResult(context: Context): Map<String, Float> {
            return mapOf(
                Pair(
                    KEY_LOCATION_UPDATES_RESULT_LAT, context.defaultSharedPreferences.getFloat(
                        KEY_LOCATION_UPDATES_RESULT_LAT, -1.0f
                    )
                ),
                Pair(
                    KEY_LOCATION_UPDATES_RESULT_LON, context.defaultSharedPreferences.getFloat(
                        KEY_LOCATION_UPDATES_RESULT_LON, -1.0f
                    )
                ),
                Pair(
                    KEY_LOCATION_UPDATES_RESULT_ALT, context.defaultSharedPreferences.getFloat(
                        KEY_LOCATION_UPDATES_RESULT_ALT, -1.0f
                    )
                ),
                Pair(
                    KEY_LOCATION_UPDATES_RESULT_ACC, context.defaultSharedPreferences.getFloat(
                        KEY_LOCATION_UPDATES_RESULT_ACC, -1.0f
                    )
                )
            )
        }

    }
}