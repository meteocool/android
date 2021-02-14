package com.meteocool.location

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import com.meteocool.location.storage.LocationDao
import com.meteocool.preferences.SharedPrefUtils

class LocationRepository(
    private val sharedPrefs: SharedPreferences,
    private val dao: LocationDao
) {

    val currentLocation: LiveData<MeteocoolLocation> = dao.getLastLocation()

}