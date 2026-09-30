package com.meteocool.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.meteocool.BuildConfig
import com.meteocool.R
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.service.LocationProviders
import com.meteocool.network.ApiClient
import com.meteocool.notifications.Notifications
import com.meteocool.notifications.RegistrationManager
import com.meteocool.preferences.Prefs
import com.meteocool.push.PushSupport
import com.meteocool.sensors.PressureReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree

/** The application object, which also holds the app-wide services. */
class MeteocoolApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var prefs: Prefs
        private set

    lateinit var registration: RegistrationManager
        private set

    // Held here: SharedPreferences keeps its listeners only weakly.
    private val registrationSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in Prefs.REGISTRATION_SETTINGS) registration.refreshRegistration()
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(if (BuildConfig.DEBUG) DebugTree() else ReleaseTree())

        // Written now, so the settings screen does not write them later and set off the listeners below.
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)
        prefs = Prefs(this)
        prefs.migrate(this)
        MeteocoolEnvironment.init(prefs)
        if (BuildConfig.DEBUG) DebugHooks.apply(prefs)
        Notifications.createChannel(this)

        registration = RegistrationManager(
            context = this,
            prefs = prefs,
            api = ApiClient(),
            pressure = PressureReader(this),
            scope = scope,
            currentLocation = { LocationProviders.currentLocation(this) },
        )
        prefs.sp.registerOnSharedPreferenceChangeListener(registrationSettingsListener)

        if (PushSupport.available && !DebugHooks.hasTestToken(prefs)) {
            scope.launch { PushSupport.fetchToken(this@MeteocoolApp)?.let { registration.setToken(it) } }
        }
        registration.refreshAuthorization()
    }

    /** Warnings and errors only. */
    private class ReleaseTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int) = priority >= Log.WARN

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            Log.println(priority, tag ?: "meteocool", message)
        }
    }
}

val Context.app: MeteocoolApp get() = applicationContext as MeteocoolApp
