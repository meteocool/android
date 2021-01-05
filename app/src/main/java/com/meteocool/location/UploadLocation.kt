package com.meteocool.location

import android.location.Location
import android.os.AsyncTask
import com.meteocool.network.JSONPost
import com.meteocool.network.NetworkUtils
import timber.log.Timber
import java.util.*

/**
 * Upload location.
 * Handover parameters in following order:
 * 1. location
 * 2. client token
 */
class UploadLocation: AsyncTask<Any, Unit, Unit>(){
    override fun doInBackground(vararg params: Any?) {
        Timber.d("location: $params[0].toString(), token: $params[1]")
        val location = params[0] as Location
        val verticalAccuracy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            location.verticalAccuracyMeters
        } else {
            -1.0f
        }

        val token =  params[1].toString()


        NetworkUtils.sendPostRequest(
            JSONPost(
                location.latitude,
                location.longitude,
                location.altitude,
                location.accuracy,
                verticalAccuracy,
                123.0,
                System.currentTimeMillis().toDouble(),
                token,
                "android",
                LocationUtils.NOTIFICATION_TIME,
                LocationUtils.NOTIFICATION_INTENSITY,
                Locale.getDefault().language
            ), NetworkUtils.POST_CLIENT_DATA
        )
    }
}
