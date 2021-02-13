package com.meteocool.ui.map

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.*
import com.meteocool.app.MeteocoolApp
import com.meteocool.location.LocationRepository
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.Resource
import com.meteocool.location.service.LocationService
import com.meteocool.network.NetworkUtils
import com.meteocool.preferences.booleanLiveData
import com.meteocool.preferences.stringLiveData
import com.meteocool.view.Event
import com.meteocool.view.VoidEvent
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

/**
 * Viewmodel for webfragment and its settings.
 */
class WebViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences =
        (application as MeteocoolApp).defaultSharedPreferences
    private val locationRepository: LocationRepository = (application as MeteocoolApp).repository
    private val foregroundLocationService: LocationService = (application as MeteocoolApp).foregroundLocationService


    private val _isZoomEnabled = sharedPreferences.booleanLiveData("map_zoom", false)
    private val _areNotificationsEnabled = sharedPreferences.booleanLiveData("notification", false)
    private val _url = sharedPreferences.stringLiveData("map_url", NetworkUtils.MAP_URL)
    private val _requestingLocationUpdatesForeground = MutableLiveData<Boolean>()
    private val _requestingSettings = MutableLiveData<VoidEvent>()

    private val _locationData = foregroundLocationService.liveData()

    val locationData: LiveData<Resource<MeteocoolLocation>> = _locationData

    val requestingLocationUpdatesForeground: LiveData<Boolean>
        get() = _requestingLocationUpdatesForeground


    val requestingSettings: LiveData<VoidEvent>
        get() = _requestingSettings

    val url: LiveData<String>
        get() = _url

    val isZoomEnabled: LiveData<Boolean>
        get() = _isZoomEnabled

    val areNotificationsEnabled: LiveData<Boolean>
        get() = _areNotificationsEnabled

    fun sendSettings() {
        Timber.d("updateSettings")
        _requestingSettings.value = VoidEvent()
    }

    fun requestForegroundLocationUpdates() {
        foregroundLocationService.requestLocationUpdates()
        _requestingLocationUpdatesForeground.value = true
    }

    fun stopForegroundLocationUpdates() {
        foregroundLocationService.stopLocationUpdates()
        _requestingLocationUpdatesForeground.value = false
    }

}

/**
 * Factory for creating a [WebViewModel] with a constructor that takes [SharedPreferences]
 * for the stored values.
 */
class WebViewModelFactory(private val application: Application) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WebViewModel(application) as T
    }
}
