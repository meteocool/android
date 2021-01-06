package com.meteocool.preferences

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.meteocool.location.MeteocoolLocation
import org.jetbrains.anko.defaultSharedPreferences

/**
 * Helper
 */
class SharedPrefUtils {
    companion object {
        const val KEY_LOCATION_LAT = "latitude"
        const val KEY_LOCATION_LON = "longitude"
        const val KEY_LOCATION_ALT = "altitude"
        const val KEY_LOCATION_ACC = "accuracy"

        /**
         * Saves location result as a string to [android.content.SharedPreferences].
         */
        fun saveResults(sharedPreferences: SharedPreferences, mLocation: Location) {
            sharedPreferences
                .edit()
                .putFloat(KEY_LOCATION_LAT, mLocation.latitude.toFloat())
                .putFloat(KEY_LOCATION_LON, mLocation.longitude.toFloat())
                .putFloat(KEY_LOCATION_ALT, mLocation.altitude.toFloat())
                .putFloat(KEY_LOCATION_ACC, mLocation.accuracy)
                .apply()
        }

        /**
         * Fetches location results from [android.content.SharedPreferences].
         */
        fun getSavedLocationResult(context: Context): MeteocoolLocation {
            return MeteocoolLocation(
                context.defaultSharedPreferences.getFloat(
                    KEY_LOCATION_LAT, -1.0f
                ).toDouble(),
                context.defaultSharedPreferences.getFloat(
                    KEY_LOCATION_LON, -1.0f
                ).toDouble(),
                context.defaultSharedPreferences.getFloat(
                    KEY_LOCATION_ALT, -1.0f
                ).toDouble(),
                context.defaultSharedPreferences.getFloat(
                    KEY_LOCATION_ACC, -1.0f
                )
            )
        }

    }
}