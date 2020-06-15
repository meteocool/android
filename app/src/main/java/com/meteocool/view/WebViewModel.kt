package com.meteocool.view

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.*
import com.meteocool.security.Validator
import com.meteocool.settings.booleanLiveData
import com.meteocool.settings.stringLiveData

class WebViewModel(private val sharedPreferences: SharedPreferences, application: Application) : AndroidViewModel(application){

    companion object {
        val test = "http://10.10.4.162:8040/"
        val prod = "https://meteocool.com/"
        const val DOC_URL = "https://meteocool.com/documentation.html"
        const val MAP_URL = "https://meteocool.com/?mobile=android2"

    }

    private val _url = sharedPreferences.stringLiveData("map_url", MAP_URL)
    private val _isMapRotateActive = sharedPreferences.booleanLiveData("map_rotate", false)
    private val _isNightModeEnabled =  sharedPreferences.booleanLiveData("map_mode", false)
    private val _isLocationGranted = MutableLiveData(Validator.isLocationPermissionGranted(application.applicationContext))

    val isLocationGranted : LiveData<Boolean>
        get() = _isLocationGranted

    val isNightModeEnabled : LiveData<Boolean>
        get() = _isNightModeEnabled

    val isMapRotateActive : LiveData<Boolean>
        get() = _isMapRotateActive

    val url : LiveData<String>
        get() = _url

    fun setUrlToDefault(){
        sharedPreferences.edit().putString("map_url",MAP_URL).apply()
    }

    fun updateLocationPermission(){
        val context = getApplication<Application>().applicationContext
        Log.d("VIEWMODEL", "perm "+ Validator.isLocationPermissionGranted(context))
        _isLocationGranted.value =  Validator.isLocationPermissionGranted(context)
    }

}
