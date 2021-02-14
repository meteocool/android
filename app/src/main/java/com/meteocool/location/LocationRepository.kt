package com.meteocool.location

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import com.meteocool.location.service.LocationService
import com.meteocool.location.storage.LocationDao
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.preferences.floatLiveData

class LocationRepository(
    private val sharedPrefs: SharedPreferences,
    private val dao: LocationDao
) {

    private val trackLocationChangeInShared : LiveData<Float> = sharedPrefs.floatLiveData(SharedPrefUtils.KEY_LOCATION_ACC, Float.NaN)

    val currentLocation: LiveData<MeteocoolLocation?> = Transformations.map(trackLocationChangeInShared) { locationAcc: Float ->
        SharedPrefUtils.getSavedLocationResult(sharedPrefs)
    }
}