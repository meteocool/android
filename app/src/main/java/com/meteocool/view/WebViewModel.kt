package com.meteocool.view

import android.app.Application
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import com.meteocool.security.Validator
import com.meteocool.settings.booleanLiveData
import com.meteocool.settings.stringLiveData
import com.meteocool.utility.NetworkUtility

class WebViewModel(private val sharedPreferences: SharedPreferences, application: Application) : AndroidViewModel(application){


    private val _url = sharedPreferences.stringLiveData("map_url", NetworkUtility.MAP_URL)
    private val _isMapRotateActive = sharedPreferences.booleanLiveData("map_rotate", false)
    private val _isNightModeEnabled =  sharedPreferences.booleanLiveData("map_mode", false)
    private val _isLocationGranted = MutableLiveData(Validator.isLocationPermissionGranted(application.applicationContext))
    private val _injectLocation = MutableLiveData<Location>()
    private val _injectSettings = MutableLiveData<Boolean>()
    private val _requestingLocationUpdatesForeground = MutableLiveData<Boolean>()

    val requestingLocationUpdatesForeground  : LiveData<Boolean>
        get() = _requestingLocationUpdatesForeground

    val injectSettings : LiveData<Boolean>
        get() = _injectSettings

    val isLocationGranted : LiveData<Boolean>
        get() = _isLocationGranted

    val isNightModeEnabled : LiveData<Boolean>
        get() = _isNightModeEnabled

    val isMapRotateActive : LiveData<Boolean>
        get() = _isMapRotateActive

    val url : LiveData<String>
        get() = _url

    val injectLocation : LiveData<Location>
        get() = _injectLocation

    fun injectLocationOnce(location : Location){
        _injectLocation.value = location
    }

    fun updateLocationPermission(){
        val context = getApplication<Application>().applicationContext
        Log.d("VIEWMODEL", "perm "+ Validator.isLocationPermissionGranted(context))
        _isLocationGranted.value =  Validator.isLocationPermissionGranted(context)
    }

    fun openDrawer(){
        _injectSettings.value = true
    }

    fun resetDrawer(){
        _injectSettings.value = false
    }

    fun sendLocationOnce(){
        _requestingLocationUpdatesForeground.value = true
    }

    fun stopLocationUpdates(){
        _requestingLocationUpdatesForeground.value = false
    }


}
