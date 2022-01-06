package com.meteocool.location.service

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.Resource
import com.meteocool.preferences.SharedPrefUtils
import com.yayandroid.locationmanager.LocationManager
import com.yayandroid.locationmanager.configuration.DefaultProviderConfiguration
import com.yayandroid.locationmanager.configuration.LocationConfiguration
import com.yayandroid.locationmanager.constants.ProviderType
import kotlinx.coroutines.Job
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class FusedLocationService(context: Context) : ForegroundLocationService(context) {

    var awesomeConfiguration = LocationConfiguration.Builder()
        .keepTracking(true)
        .useDefaultProviders(
            DefaultProviderConfiguration.Builder()
                .requiredTimeInterval((5 * 1000).toLong())
                .requiredDistanceInterval(0)
                .acceptableAccuracy(5.0f)
                .acceptableTimePeriod((5 * 1000).toLong())
                .setWaitPeriod(ProviderType.GPS, (2 * 1000).toLong())
                .setWaitPeriod(ProviderType.NETWORK, (2 * 1000).toLong())
                .build()
        )
        .build()

    private val locationManager: LocationManager =
        LocationManager.Builder(context)
            .configuration(awesomeConfiguration)
            .notify(com.meteocool.location.LocationListener(context))
            .build()

    private var resultAsLiveData: MutableLiveData<Resource<MeteocoolLocation>> =
        MutableLiveData(Resource(SharedPrefUtils.getSavedLocationResult(context.defaultSharedPreferences)))

    private lateinit var job: Job

    init {
    }

    override fun requestLocationUpdates() {
        Timber.d("Request Updates")
        locationManager.get()
        try {
        } catch (e: SecurityException) {
            Timber.e(e)
        }
    }

    override fun stopLocationUpdates() {
        Timber.d("Stopped")
        if (this::job.isInitialized) {
            job.cancel()
        }
    }

    override fun liveData() = resultAsLiveData
}