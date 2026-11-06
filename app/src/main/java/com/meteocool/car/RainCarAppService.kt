package com.meteocool.car

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.validation.HostValidator
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.meteocool.BuildConfig
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.preferences.Prefs
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.MeteocoolActivity
import com.meteocool.weather.WeatherData
import com.meteocool.widgets.WidgetData
import com.meteocool.widgets.WidgetGraphics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RainCarAppService : CarAppService() {
    private var foreground = false
    @SuppressLint("PrivateResource") // Android's CarAppService guide specifies this maintained host allowlist.
    override fun createHostValidator(): HostValidator = if (BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(this).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = RainStatusScreen(carContext, ::active)
    }

    @SuppressLint("ForegroundServiceType") // AGP 8.13 checks the GMS flavor manifest; location is declared in main.
    private fun active(enabled: Boolean): Boolean {
        if (!enabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            return false
        }
        if (foreground) return true
        if (!PermUtils.isLocationPermissionGranted(this)) return false
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.car_rain_status), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, ID, Intent(this, MeteocoolActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.png_firebase_push)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.car_rain_status))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        return try {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            foreground = true
            true
        } catch (_: SecurityException) { false } catch (_: IllegalStateException) { false }
    }

    override fun onDestroy() { active(false); super.onDestroy() }

    companion object {
        const val ID = 703
        private const val CHANNEL = "car_weather"
    }
}

/** Stable row titles make updates refreshes, so polling never consumes the host's task depth. */
class RainStatusScreen(context: CarContext, private val active: (Boolean) -> Boolean) : Screen(context), DefaultLifecycleObserver {
    private var status = context.getString(R.string.car_loading)
    private var observation = context.getString(R.string.car_loading)
    private var request: Job? = null
    private var timer: Job? = null
    private var environment: Job? = null
    private val settings = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.NOTIFICATION_INTENSITY) refresh()
    }

    init { lifecycle.addObserver(this) }

    override fun onStart(owner: LifecycleOwner) {
        carContext.app.prefs.sp.registerOnSharedPreferenceChangeListener(settings)
        timer = lifecycleScope.launch {
            while (isActive) { refresh(); delay(60_000) }
        }
        environment = lifecycleScope.launch { MeteocoolEnvironment.changes.drop(1).collect { refresh() } }
    }

    override fun onStop(owner: LifecycleOwner) {
        carContext.app.prefs.sp.unregisterOnSharedPreferenceChangeListener(settings)
        timer?.cancel(); environment?.cancel(); request?.cancel()
        active(false)
        status = carContext.getString(R.string.car_loading)
        observation = carContext.getString(R.string.car_loading)
    }

    private fun refresh() {
        request?.cancel()
        status = carContext.getString(R.string.car_loading)
        observation = carContext.getString(R.string.car_loading)
        invalidate()
        request = lifecycleScope.launch {
            if (!active(true)) {
                status = carContext.getString(R.string.widget_no_location)
                observation = carContext.getString(R.string.car_location_help)
                invalidate()
                return@launch
            }
            val origin = MeteocoolEnvironment.currentApiBase
            val threshold = carContext.app.prefs.intensityDbz
            val location = WidgetData.currentLocation(carContext, car = true)
            if (location == null) {
                status = carContext.getString(R.string.widget_no_location)
                observation = carContext.getString(R.string.car_location_help)
                invalidate()
                return@launch
            }
            val forecast = WeatherData.forecast(carContext, location.latitude, location.longitude, threshold.toDouble())
            if (origin != MeteocoolEnvironment.currentApiBase || threshold != carContext.app.prefs.intensityDbz) return@launch
            val now = System.currentTimeMillis()
            if (!WidgetData.usableLocation(location, now, 60_000)) {
                status = carContext.getString(R.string.widget_no_location)
                observation = carContext.getString(R.string.car_location_help)
            } else if (forecast == null || !forecast.fresh(now / 1000)) {
                status = carContext.getString(R.string.widget_unavailable)
                observation = carContext.getString(R.string.car_retry)
            } else {
                status = WidgetGraphics.summary(carContext, forecast, now / 1000)
                observation = WidgetGraphics.time(carContext, forecast.at((forecast.observed - 1).coerceAtLeast(0)))
            }
            invalidate()
        }
    }

    @Suppress("DEPRECATION") // Header needs Car API 7; weather apps also support hosts on API 6.
    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(carContext.getString(R.string.car_rain_status)).addText(status).build())
            .addRow(Row.Builder().setTitle(carContext.getString(R.string.car_observation)).addText(observation).build())
            .addAction(Action.Builder().setTitle(carContext.getString(R.string.widget_refresh)).setOnClickListener { refresh() }.build())
        if (!PermUtils.isLocationPermissionGranted(carContext)) {
            pane.addAction(Action.Builder().setTitle(carContext.getString(R.string.allow_location_access))
                .setOnClickListener(ParkedOnlyOnClickListener.create {
                    carContext.requestPermissions(PermUtils.LOCATION_PERMISSIONS.toList()) { _, _ -> refresh() }
                }).build())
        }
        return PaneTemplate.Builder(pane.build()).setTitle(carContext.getString(R.string.app_name) + if (MeteocoolEnvironment.current == MeteocoolEnvironment.DEMO)
        " · ${carContext.getString(R.string.mode_demo)}" else "").setHeaderAction(Action.APP_ICON).build()
    }
}
