package com.meteocool.location

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.preferences.floatLiveData
import com.meteocool.preferences.stringLiveData

class LocationRepository(
    private val sharedPrefs: SharedPreferences
) {
    private val trackLocationChangeInShared : LiveData<Float> = sharedPrefs.floatLiveData(SharedPrefUtils.KEY_LOCATION_ACC, Float.NaN)

    val currentLocation: LiveData<MeteocoolLocation?> = Transformations.map(trackLocationChangeInShared) { locationAcc: Float ->
        SharedPrefUtils.getSavedLocationResult(sharedPrefs)
    }
}