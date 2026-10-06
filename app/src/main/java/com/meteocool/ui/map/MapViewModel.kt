package com.meteocool.ui.map

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.service.LocationProviders
import com.meteocool.preferences.Prefs
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The map screen's state: the location button, the page's readiness and the
 * settings the page is shown.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    /** off -> active (show the dot, centre once) -> tracking (follow every fix) -> off. */
    enum class LocationButtonState { OFF, ACTIVE, TRACKING }

    val prefs: Prefs = application.app.prefs

    val locationService = LocationProviders.foreground(application)

    val fixes: LiveData<MeteocoolLocation> = locationService.fixes

    private val _buttonState = MutableLiveData(LocationButtonState.OFF)
    val buttonState: LiveData<LocationButtonState> = _buttonState

    /** Follow every fix. */
    var autoFocus = false

    /** Centre on the next fix only. */
    var autoFocusOnce = false

    /** Zoom in on the next fix only. */
    var zoomOnce = false

    /** The page has called requestSettings(), so its window functions exist. */
    var pageReady = false

    private val _mapUrl = MutableLiveData(MeteocoolEnvironment.currentMapUrl)
    val mapUrl: LiveData<String> = _mapUrl

    private val _webSettingsVersion = MutableLiveData(0)

    /** Bumped whenever a setting the page shows changes. */
    val webSettingsVersion: LiveData<Int> = _webSettingsVersion

    // A field, not a lambda at the call site: SharedPreferences holds listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in Prefs.WEB_SETTINGS) _webSettingsVersion.value = (_webSettingsVersion.value ?: 0) + 1
    }

    init {
        prefs.sp.registerOnSharedPreferenceChangeListener(listener)
        // Mode, or the demo notice's "Disable Demo Mode", switched deployment.
        viewModelScope.launch { MeteocoolEnvironment.changes.drop(1).collect { reloadMap() } }
    }

    fun setButtonState(state: LocationButtonState) {
        _buttonState.value = state
    }

    /** Loads the map again: after a failure, and after the environment changed. */
    fun reloadMap() {
        _mapUrl.value = MeteocoolEnvironment.currentMapUrl
    }

    /** `window.settings.injectSettings()` takes these keys. */
    fun webSettings(): Map<String, Any> = mapOf(
        "mapRotation" to prefs.mapRotate,
        "radarColorMapping" to prefs.radarColorMapping,
        "mapBaseLayer" to prefs.baseLayer,
        "experimentalFeatures" to (MeteocoolEnvironment.current == MeteocoolEnvironment.STAGING),
    )

    override fun onCleared() {
        super.onCleared()
        prefs.sp.unregisterOnSharedPreferenceChangeListener(listener)
        locationService.stop()
    }
}
