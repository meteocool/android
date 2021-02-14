package com.meteocool.location

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MeteocoolLocation(
    @PrimaryKey val uid: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val verticalAccuracy: Float,
    val elapsedNanosSinceBoot: Long
){
    companion object{
        const val  KEY_LATITUDE = "lat"
        const val  KEY_LONGITUDE = "lon"
        const val  KEY_ALTITUDE = "altitude"
        const val  KEY_ACCURACY = "accuracy"
        const val  KEY_VERTICAL_ACCURACY = "verticalAccuracy"
        const val  KEY_ELAPSED_NANOS = "elapsedNanos"
    }
}

class MeteocoolLocationFactory{
    companion object{
        fun new(map: Map<String, Any>) =
            MeteocoolLocation(1,
                map[MeteocoolLocation.KEY_LATITUDE] as Double,
                map[MeteocoolLocation.KEY_LONGITUDE] as Double,
                map[MeteocoolLocation.KEY_ALTITUDE] as Double,
                map[MeteocoolLocation.KEY_ACCURACY] as Float,
                map[MeteocoolLocation.KEY_VERTICAL_ACCURACY] as Float,
                map[MeteocoolLocation.KEY_ELAPSED_NANOS] as Long
            )
    }
}

