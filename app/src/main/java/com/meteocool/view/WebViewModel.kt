package com.meteocool.view

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.*
import com.meteocool.settings.booleanLiveData
import com.meteocool.settings.stringLiveData
import com.meteocool.utility.NetworkUtils
import timber.log.Timber

class WebViewModel(private val sharedPreferences: SharedPreferences, application: Application) : AndroidViewModel(application){

    private val _url = sharedPreferences.stringLiveData("map_url", NetworkUtils.MAP_URL)
    private val _requestingLocationUpdatesBackground = MutableLiveData<Event<Boolean>>()
    private val _injectDrawer = MutableLiveData<VoidEvent>()
    private val _requestingLocationUpdatesForeground = MutableLiveData<Event<Boolean>>()
    private val _requestingSettings = MutableLiveData<VoidEvent>()

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

}
