package com.meteocool.location

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import com.meteocool.preferences.stringLiveData

class LocationRepository(
    private val sharedPrefs: SharedPreferences
) {

    private val trackLocationChangeInShared : LiveData<String> = sharedPrefs.stringLiveData("location", "")

    val currentLocation: LiveData<MeteocoolLocation?> = Transformations.map(trackLocationChangeInShared) { location: String ->
        getLocationFromShared(location)
    }

    private fun getLocationFromShared(location : String) : MeteocoolLocation?{
        if(location.isEmpty()){
            return null
        }
        val lat =  sharedPrefs.getFloat("lat", Float.NaN).toDouble()
        val lon =  sharedPrefs.getFloat("lon", Float.NaN).toDouble()
        val alt =  sharedPrefs.getFloat("alt", Float.NaN).toDouble()
        val acc =  sharedPrefs.getFloat("acc", Float.NaN)

        return MeteocoolLocation(lat, lon, alt, acc)
    }
}