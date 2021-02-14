package com.meteocool.location

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.common.util.concurrent.ListenableFuture
import com.meteocool.network.NetworkUtils
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences

class ListenableLocationUpdateWorker(private val context: Context, params: WorkerParameters) :
    ListenableWorker(context, params) {

    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)


    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if(location != null) {
                        SharedPrefUtils.saveResults(context.defaultSharedPreferences, location)
                        WorkManager.getInstance(applicationContext)
                            .enqueue(UploadWorker.createRequest(Data.EMPTY))
                        it.set(Result.success())
                    }else{
                        //TODO Request updates, but maybe even not needed.
                        it.set(Result.retry())
                    }
                }
            }catch(e : SecurityException){
                it.set(Result.failure())
            }
        }
    }
}