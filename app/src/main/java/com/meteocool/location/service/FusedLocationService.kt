package com.meteocool.location.service

import com.meteocool.location.Resource
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.MeteocoolLocationFactory
import com.meteocool.location.storage.LocationPersistenceWorker
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class FusedLocationService(context: Context) : ForegroundLocationService(context) {

    /**
     * The entry point to Google Play Services.
     */


    private var resultAsLiveData: MutableLiveData<Resource<MeteocoolLocation>> =
        MutableLiveData(Resource(SharedPrefUtils.getSavedLocationResult(context.defaultSharedPreferences)))


    private lateinit var job: Job

    init {
    }

    override fun requestLocationUpdates() {
        Timber.d("Request Updates")
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
