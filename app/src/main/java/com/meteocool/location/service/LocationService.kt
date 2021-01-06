package com.meteocool.location.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.meteocool.location.LocationUpdatesBroadcastReceiver
import com.meteocool.location.MeteocoolLocation
import java.util.concurrent.TimeUnit

abstract class LocationService(protected val context : Context){

    companion object{
        const val BACKGROUND_SETTING = 998
        const val FOREGROUND_SETTING = 1000
        const val REQUEST_CHECK_GPS_SETTINGS = 999
    }

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    protected var updateInterval : Long = TimeUnit.MINUTES.toMillis(15)

    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value, but they may be less frequent.
     */
    protected var fastestUpdateInterval : Long = TimeUnit.MINUTES.toMillis(10)

    /**
     * The max time before batched results are delivered by location services. Results may be
     * delivered sooner than this interval.
     */
    protected var maxWaitTime : Long = TimeUnit.MINUTES.toMillis(15)


    protected val pendingIntent : PendingIntent
        get() {
            val intent = Intent(context, LocationUpdatesBroadcastReceiver::class.java)
            intent.action = LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

    abstract fun requestLocationUpdates()
    abstract fun stopLocationUpdates()

}