package com.meteocool.location

import android.location.Location
import android.os.Build

/**
 * One location fix. Values the device did not report are -1, which is what
 * the backend expects for them.
 */
data class MeteocoolLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val verticalAccuracy: Float,
    val speed: Float,
    val course: Float,
    /** Wall-clock time of the fix, ms since the epoch. */
    val timeMillis: Long,
    /** Monotonic time of the fix, for measuring its age. */
    val elapsedRealtimeNanos: Long,
) {
    companion object {
        fun from(location: Location) = MeteocoolLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else -1.0,
            accuracy = if (location.hasAccuracy()) location.accuracy else -1f,
            verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters
            } else {
                -1f
            },
            speed = if (location.hasSpeed()) location.speed else -1f,
            course = if (location.hasBearing()) location.bearing else -1f,
            timeMillis = location.time,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
        )
    }
}
