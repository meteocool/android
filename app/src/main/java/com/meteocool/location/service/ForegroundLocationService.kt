package com.meteocool.location.service

import com.meteocool.location.Resource
import android.content.Context
import androidx.lifecycle.LiveData
import com.meteocool.location.MeteocoolLocation
import java.util.concurrent.TimeUnit

abstract class ForegroundLocationService(protected val context : Context){

    companion object{
        const val BACKGROUND_SETTING = 998
        const val FOREGROUND_SETTING = 1000
        const val REQUEST_CHECK_GPS_SETTINGS = 999
    }

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    protected var updateInterval : Long = TimeUnit.SECONDS.toMillis(20)

    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value, but they may be less frequent.
     */
    protected var fastestUpdateInterval : Long = TimeUnit.SECONDS.toMillis(10)

    /**
     * The max time before batched results are delivered by location services. Results may be
     * delivered sooner than this interval.
     */
    protected var maxWaitTime : Long =  TimeUnit.SECONDS.toMillis(20)

    abstract fun requestLocationUpdates()
    abstract fun stopLocationUpdates()
    abstract fun liveData() : LiveData<Resource<MeteocoolLocation>>

}