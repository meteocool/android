package com.meteocool.network

import android.content.Context
import android.location.Location
import android.util.Log
import com.meteocool.location.LocationResultHelper
import com.meteocool.utility.JSONPost
import com.meteocool.utility.NetworkUtility
import java.util.*

/*class UploadWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        // Do the work here--in this case, upload the images.

        /*Log.d("Async", "location: $workerParams[0].toString(), token: $params[1]")
        val location = params[0] as Location
        val verticalAccuracy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            location.verticalAccuracyMeters
        } else {
            -1.0f
        }

        val token =  params[1].toString()


        NetworkUtility.sendPostRequest(
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
                LocationResultHelper.NOTIFICATION_TIME,
                LocationResultHelper.NOTIFICATION_INTENSITY,
                Locale.getDefault().language
            ), NetworkUtility.POST_CLIENT_DATA
        )
        */


        // Indicate whether the task finished successfully with the Result
        return Result.success()
    }
}

*/
