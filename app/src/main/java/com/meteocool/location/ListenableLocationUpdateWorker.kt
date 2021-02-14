package com.meteocool.location

import android.content.Context
import android.location.Location
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.common.util.concurrent.ListenableFuture
import com.meteocool.network.NetworkUtils
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences
import java.util.*

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
                            .enqueue(UploadWorker.createRequest(createInputData(location)))
                        it.set(Result.success())
                    }else{
                        //TODO Request updates, but maybe not even needed.
                        it.set(Result.retry())
                    }
                }
            }catch(e : SecurityException){
                it.set(Result.failure())
            }
        }
    }

    private fun createInputData(location : Location) : Data{
        val verticalMeters = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            location.verticalAccuracyMeters
        } else{
            -1.0
        }
        val prefs = context.defaultSharedPreferences

        return UploadWorker.createInputData(
            mapOf(
                Pair("url", NetworkUtils.POST_CLIENT_DATA.toString()),
                Pair("lat", location.latitude),
                Pair("lon", location.longitude),
                Pair("altitude", location.altitude),
                Pair("accuracy", location.accuracy),
                Pair("verticalAccuracy", verticalMeters),
                Pair("pressure", 123.0),
                Pair("timestamp", System.currentTimeMillis().toDouble()),
                Pair("token", SharedPrefUtils.getFirebaseToken(prefs)),
                Pair("source", "android"),
                Pair("ahead", prefs.getString("notification_time", "15")!!.toInt()), //TODO
                Pair("intensity", prefs.getString("notification_intensity", "10")!!.toInt()), //TODO
                Pair("lang", Locale.getDefault().language)
            )
        )
    }
}