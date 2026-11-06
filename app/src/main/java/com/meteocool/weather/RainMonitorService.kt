package com.meteocool.weather

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.annotation.SuppressLint
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meteocool.R
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.service.LocationProviders
import com.meteocool.permissions.PermUtils
import kotlinx.coroutines.*

/** A bounded monitoring session, started only by the reader's Track this rain button. */
class RainMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType") // AGP 8.13 checks the GMS flavor manifest; the types are declared in main.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || !PermUtils.areNotificationsEnabled(this) || !PermUtils.isLocationPermissionGranted(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        if (running?.isActive == true) return START_NOT_STICKY
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.rain_monitor), NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification(getString(R.string.rain_checking)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        running = scope.launch {
            val origin = MeteocoolEnvironment.currentApiBase
            val began = android.os.SystemClock.elapsedRealtime()
            try {
                while (isActive && android.os.SystemClock.elapsedRealtime() - began < 2 * 60 * 60 * 1000 &&
                    origin == MeteocoolEnvironment.currentApiBase && PermUtils.areNotificationsEnabled(this@RainMonitorService)) {
                    val fix = LocationProviders.currentLocation(this@RainMonitorService)
                    val forecast = fix?.takeIf { System.currentTimeMillis() - it.timeMillis in 0..60_000 && it.accuracy in 0f..1000f }
                        ?.let { WeatherData.forecast(this@RainMonitorService, it.latitude, it.longitude) }
                    if (origin != MeteocoolEnvironment.currentApiBase) break
                    val phase = forecast?.phase()
                    val text = forecast?.let { RainText.summary(this@RainMonitorService, it) } ?: getString(R.string.rain_unavailable)
                    getSystemService(NotificationManager::class.java).notify(ID, notification(text))
                    if (phase == RainForecast.Phase.Dry) break
                    delay(60_000)
                }
            } finally { stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): android.app.Notification {
        val stop = PendingIntent.getService(this, 0, Intent(this, RainMonitorService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, RainStatusActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.png_firebase_push)
            .setContentTitle(getString(R.string.rain_monitor)).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOngoing(true)
            .setOnlyAlertOnce(true).setRequestPromotedOngoing(true).setContentIntent(open).setDeleteIntent(stop)
            .addAction(0, getString(R.string.rain_stop), stop).setTimeoutAfter(3 * 60_000).build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onDestroy() { scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }

    companion object {
        const val ID = 702
        const val CHANNEL = "rain_monitor"
        const val STOP = "com.meteocool.STOP_RAIN"
    }
}
