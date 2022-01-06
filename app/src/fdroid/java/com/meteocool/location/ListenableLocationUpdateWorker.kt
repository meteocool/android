package com.meteocool.location

import android.content.Context
import android.location.Criteria
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import com.yayandroid.locationmanager.LocationManager
import com.yayandroid.locationmanager.configuration.Configurations

class ListenableLocationUpdateWorker(context: Context, params: WorkerParameters) :
    ListenableWorker(context, params) {


    private val locationManager: LocationManager =
         LocationManager.Builder(context)
        .configuration(Configurations.silentConfiguration())
        .notify(LocationListener(context))
        .build()

    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture { completer ->
            try {
                locationManager.get()
                completer.set(Result.success())
            } catch (e: SecurityException) {
                completer.set(Result.failure())
            }
        }
    }
}
