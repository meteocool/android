package com.meteocool.location.storage

import android.content.Context
import android.location.Location
import androidx.work.*
import com.meteocool.location.MeteocoolLocation
import com.meteocool.network.UploadWorker
import timber.log.Timber

class LocationPersistenceWorker(private val context: Context, params: WorkerParameters) :
    Worker(context, params)  {

    override fun doWork(): Result {
        val loc = MeteocoolLocation(uid = 1,
            latitude = inputData.keyValueMap[MeteocoolLocation.KEY_LATITUDE] as Double,
            longitude = inputData.keyValueMap[MeteocoolLocation.KEY_LONGITUDE] as Double,
            altitude = inputData.keyValueMap[MeteocoolLocation.KEY_ALTITUDE] as Double,
            accuracy = inputData.keyValueMap[MeteocoolLocation.KEY_ACCURACY] as Float,
            verticalAccuracy = inputData.keyValueMap[MeteocoolLocation.KEY_VERTICAL_ACCURACY] as Float,
            elapsedNanosSinceBoot = inputData.keyValueMap[MeteocoolLocation.KEY_ELAPSED_NANOS] as Long,
        )
        Timber.d("$loc")
        insertOrUpdateLocation(loc)
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