package com.meteocool.location.storage

import android.content.Context
import android.location.Location
import androidx.work.*
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.MeteocoolLocationFactory
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class LocationPersistenceWorker(private val context: Context, params: WorkerParameters) :
    Worker(context, params)  {

    override fun doWork(): Result {
        val meteocoolLocation = MeteocoolLocationFactory.new(inputData.keyValueMap)
        Timber.d("$meteocoolLocation")
        SharedPrefUtils.saveResults(context.defaultSharedPreferences, meteocoolLocation)
//        insertOrUpdateLocation(meteocoolLocation)
        return Result.success()
    }

    private fun insertOrUpdateLocation(location: MeteocoolLocation) {
        val isEntryExisting =
            BasicLocationDatabase.getDatabase(context).meteoLocationDao().isExists()
        if (isEntryExisting) {
            BasicLocationDatabase.getDatabase(context).meteoLocationDao().updateLocation(location)
        }else{
            BasicLocationDatabase.getDatabase(context).meteoLocationDao().insertLocation(location)
        }
    }

    companion object{
        fun createRequest(inputData: Data): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<LocationPersistenceWorker>()
                .setInputData(inputData)
                .build()
        }

        fun createMeteocooLocationData(location: Location) : Data{
            val verticalAccuracy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                location.verticalAccuracyMeters
            } else {
                -1.0f
            }
            return UploadWorker.createInputData(
                mapOf(
                    Pair(MeteocoolLocation.KEY_LATITUDE, location.latitude),
                    Pair(MeteocoolLocation.KEY_LONGITUDE, location.longitude),
                    Pair(MeteocoolLocation.KEY_ALTITUDE, location.altitude),
                    Pair(MeteocoolLocation.KEY_ACCURACY, location.accuracy),
                    Pair(MeteocoolLocation.KEY_VERTICAL_ACCURACY, verticalAccuracy),
                    Pair(MeteocoolLocation.KEY_ELAPSED_NANOS, location.elapsedRealtimeNanos),
                )
            )
        }
    }
}