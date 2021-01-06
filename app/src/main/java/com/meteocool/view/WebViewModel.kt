package com.meteocool.view

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.*
import com.meteocool.location.LocationRepository
import com.meteocool.location.MeteocoolLocation
import com.meteocool.preferences.booleanLiveData
import com.meteocool.preferences.stringLiveData
import com.meteocool.network.NetworkUtils
import timber.log.Timber

/**
 * Viewmodel for webfragment and its settings.
 */
class WebViewModel(private val sharedPreferences: SharedPreferences, private val locationRepository: LocationRepository, application: Application) : ViewModel(){

    private val _isZoomEnabled = sharedPreferences.booleanLiveData("map_zoom", false)
    private val _areNotificationsEnabled = sharedPreferences.booleanLiveData("notification", false)
    private val _url = sharedPreferences.stringLiveData("map_url", NetworkUtils.MAP_URL)
    private val _injectDrawer = MutableLiveData<VoidEvent>()
    private val _requestingLocationUpdatesForeground = MutableLiveData<Event<Boolean>>()
    private val _requestingSettings = MutableLiveData<VoidEvent>()

    private val _locationData = locationRepository.currentLocation

    val locationData : LiveData<MeteocoolLocation?>
        get() = _locationData

    val requestingLocationUpdatesForeground  : LiveData<Event<Boolean>>
        get() = _requestingLocationUpdatesForeground


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

    fun sendSettings(){
        Timber.d("updateSettings")
        _requestingSettings.value = VoidEvent()
    }

}
