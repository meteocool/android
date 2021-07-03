package com.meteocool.preferences

import android.content.SharedPreferences
import android.location.Location
import com.meteocool.preferences.FirebaseMessagingWrapper
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
        private const val KEY_LOCATION_NANOS = "elapsedNanos"
        private const val KEY_FIREBASE_TOKEN = "fb_token"

        /**
         * Saves location result as a string to [android.content.SharedPreferences].
         */
        fun saveResults(sharedPrefs: SharedPreferences, mLocation: MeteocoolLocation) {
            sharedPrefs
                .edit()
                .putFloat(KEY_LOCATION_LAT, mLocation.latitude.toFloat())
                .putFloat(KEY_LOCATION_LON, mLocation.longitude.toFloat())
                .putFloat(KEY_LOCATION_ALT, mLocation.altitude.toFloat())
                .putFloat(KEY_LOCATION_ACC, mLocation.accuracy)
                .putLong(KEY_LOCATION_NANOS, mLocation.elapsedNanosSinceBoot)
                .apply()
        }

        fun saveFirebaseToken(sharedPrefs: SharedPreferences, token: String) {
            sharedPrefs.edit().putString(KEY_FIREBASE_TOKEN, token).apply()
        }

        fun getFirebaseToken(sharedPrefs: SharedPreferences): String {
            var token= sharedPrefs.getString(KEY_FIREBASE_TOKEN, "no token")!!
            if(token == "no token") {
                token = FirebaseMessagingWrapper.getFirebaseToken()
                saveFirebaseToken(sharedPrefs, token)
            }
            return token
        }

        /**
         * Fetches location results from [android.content.SharedPreferences].
         */
        fun getSavedLocationResult(sharedPrefs: SharedPreferences) : MeteocoolLocation {
            return MeteocoolLocation(
                1,
                sharedPrefs.getFloat(
                    KEY_LOCATION_LAT, -1.0f
                ).toDouble(),
                sharedPrefs.getFloat(
                    KEY_LOCATION_LON, -1.0f
                ).toDouble(),
                sharedPrefs.getFloat(
                    KEY_LOCATION_ALT, -1.0f
                ).toDouble(),
                sharedPrefs.getFloat(
                    KEY_LOCATION_ACC, -1.0f
                ),
                123f,
                sharedPrefs.getLong(
                    KEY_LOCATION_NANOS, -1
                )
            )
        }

    }
}
