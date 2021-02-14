package com.meteocool.location

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

@Entity
data class MeteocoolLocation(
    @PrimaryKey val uid: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val verticalAccuracy: Float,
    val elapsedNanosSinceBoot: Long
) : Comparable<MeteocoolLocation?> {
    override fun compareTo(other: MeteocoolLocation?): Int {
        if(other == null){
            return 1
        }

        val adjustedLat = (this.latitude * 1000).roundToInt() / 1000.0
        val adjustedLatOther = (other.latitude * 1000).roundToInt() / 1000.0

        val adjustedLon = (this.longitude * 1000).roundToInt() / 1000.0
        val adjustedLonOther = (other.longitude * 1000).roundToInt() / 1000.0

        if(adjustedLat == adjustedLatOther && adjustedLon == adjustedLonOther){
            return (this.accuracy - other.accuracy).toInt()
        }

        return (this.elapsedNanosSinceBoot - other.elapsedNanosSinceBoot).toInt()
    }


}
