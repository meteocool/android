package com.meteocool.ui.map

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import com.meteocool.app.MeteocoolApp
import com.meteocool.location.LocationRepository
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.Resource
import com.meteocool.location.service.ForegroundLocationService
import com.meteocool.location.service.LocationServiceFactory
import com.meteocool.network.NetworkUtils
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.view.VoidEvent
import timber.log.Timber

/**
 * Viewmodel for webfragment and its settings.
 */
class WebViewModel(application: Application) : AndroidViewModel(application),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val locationRepository: LocationRepository = getApplication<MeteocoolApp>().repository
    private val foregroundLocationService: ForegroundLocationService =
        LocationServiceFactory.getLocationService(application.applicationContext)


    private val _isZoomEnabled = MutableLiveData<Boolean>()
    val isZoomEnabled: LiveData<Boolean> get() = _isZoomEnabled

    private val _areNotificationsEnabled = MutableLiveData<Boolean>()
    val areNotificationsEnabled: LiveData<Boolean> get() = _areNotificationsEnabled

    private val _url = MutableLiveData(NetworkUtils.MAP_URL)
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



    override fun onSharedPreferenceChanged(p0: SharedPreferences?, key: String?) {
        Timber.d("OnSharedPref was changed $key")
        when(key){
            "notification" ->  {
                _areNotificationsEnabled.value = sharedPreferences.getBoolean("notification", false)
                if (sharedPreferences.getBoolean("notification", false)) {
                    val data = UploadWorker.createInputData(
                        mapOf(
                            Pair("url", NetworkUtils.POST_UNREGISTER_TOKEN.toString()),
                            Pair(
                                "token",
                                SharedPrefUtils.getFirebaseToken(sharedPreferences)
                            )
                        )
                    )
                    WorkManager.getInstance(getApplication())
                        .enqueue(UploadWorker.createRequest(data))
                        .result
                } else {
                    val data = UploadWorker.createDataForLocationPost(
                        sharedPreferences,
                        SharedPrefUtils.getSavedLocationResult(sharedPreferences)
                    )
                    WorkManager.getInstance(getApplication())
                        .enqueue(UploadWorker.createRequest(data))
                        .result
                }
            }
            "map_zoom" ->  _isZoomEnabled.value = sharedPreferences.getBoolean("map_zoom", false)
            "notification_details", "notification_intensity", "notification_time" -> {
                val data = UploadWorker.createDataForLocationPost(
                    sharedPreferences,
                    SharedPrefUtils.getSavedLocationResult(sharedPreferences)
                )
                WorkManager.getInstance(getApplication())
                    .enqueue(UploadWorker.createRequest(data))
                    .result
            }
            "map_rotate" -> {
                sendSettings()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
}

/**
 * Factory for creating a [WebViewModel] which inherits from [AndroidViewModel].
 */
class WebViewModelFactory(private val application: Application) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WebViewModel(application) as T
    }
}
