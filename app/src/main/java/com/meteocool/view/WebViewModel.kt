package com.meteocool.view

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.*
import com.meteocool.location.LocationLiveData
import com.meteocool.sharedPrefs.booleanLiveData
import com.meteocool.sharedPrefs.stringLiveData
import com.meteocool.network.NetworkUtils
import timber.log.Timber

/**
 * Viewmodel for webfragment and its settings.
 */
class WebViewModel(private val sharedPreferences: SharedPreferences, application: Application) : AndroidViewModel(application){

    private val _isZoomEnabled = sharedPreferences.booleanLiveData("map_zoom", false)
    private val _areNotificationsEnabled = sharedPreferences.booleanLiveData("notification", false)
    private val _url = sharedPreferences.stringLiveData("map_url", NetworkUtils.MAP_URL)
    private val _requestingLocationUpdatesBackground = MutableLiveData<Event<Boolean>>()
    private val _injectDrawer = MutableLiveData<VoidEvent>()
    private val _requestingLocationUpdatesForeground = MutableLiveData<Event<Boolean>>()
    private val _requestingSettings = MutableLiveData<VoidEvent>()

    private val _locationData = LocationLiveData(application)

    private val _requesting = MutableLiveData<Boolean>()

    val locationData : LocationLiveData
        get() = _locationData

    //fun getLocationData() = _locationData

    val requestingLocationUpdatesForeground  : LiveData<Event<Boolean>>
        get() = _requestingLocationUpdatesForeground

    val requestingLocationUpdatesBackground  : LiveData<Event<Boolean>>
        get() = _requestingLocationUpdatesBackground

    val requestingSettings  : LiveData<VoidEvent>
        get() = _requestingSettings

    val injectDrawer : LiveData<VoidEvent>
        get() = _injectDrawer

    val url : LiveData<String>
        get() = _url

    val isZoomEnabled : LiveData<Boolean>
        get() = _isZoomEnabled

    val areNotificationsEnabled : LiveData<Boolean>
        get() = _areNotificationsEnabled

    fun openDrawer(){
        _injectDrawer.value = VoidEvent()
    }

    fun sendLocationOnce(isLocationGranted : Boolean){
        Timber.d("locationOnceSent $isLocationGranted")
        _requestingLocationUpdatesForeground.value = Event(isLocationGranted)
    }

    fun requestingBackgroundLocationUpdates(isLocationGranted : Boolean){
        Timber.d("requestingBackgroundLocationUpdates $isLocationGranted")
        _requestingLocationUpdatesBackground.value = Event(isLocationGranted)
    }

    fun sendSettings(){
        Timber.d("updateSettings")
        _requestingSettings.value = VoidEvent()
    }

    fun getLocation(){
        _locationData.test()
    }

}
