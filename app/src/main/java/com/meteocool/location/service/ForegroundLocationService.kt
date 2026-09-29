package com.meteocool.location.service

import android.content.Context
import android.content.IntentSender
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.meteocool.app.app
import com.meteocool.location.MeteocoolLocation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.TimeUnit

/**
 * Location updates while the map is on screen. Each fix goes to the map, to
 * the saved last location and to the push registration.
 */
abstract class ForegroundLocationService(protected val context: Context) {

    protected val updateInterval: Long = TimeUnit.SECONDS.toMillis(10)
    protected val fastestUpdateInterval: Long = TimeUnit.SECONDS.toMillis(5)

    private val _fixes = MutableLiveData<MeteocoolLocation>()
    val fixes: LiveData<MeteocoolLocation> = _fixes

    private val _resolutions = MutableSharedFlow<IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Location settings the user has to change first, e.g. turning location on. */
    val resolutions: SharedFlow<IntentSender> = _resolutions

    var isRunning = false
        protected set

    abstract fun start()
    abstract fun stop()

    /** Called on the main thread. */
    protected fun deliver(location: Location) {
        val fix = MeteocoolLocation.from(location)
        _fixes.value = fix
        val app = context.app
        app.prefs.saveLastLocation(fix)
        app.registration.onLocation(fix, background = false)
    }

    protected fun requestResolution(intentSender: IntentSender) {
        _resolutions.tryEmit(intentSender)
    }
}
