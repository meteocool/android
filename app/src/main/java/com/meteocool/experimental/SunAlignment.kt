package com.meteocool.experimental

import kotlin.math.*

/** NOAA solar-position equations, matching iOS GeoFrame.SunPosition, including atmospheric refraction. */
object SunAlignment {
    data class Position(val azimuth: Double, val elevation: Double)
    enum class Failure { BELOW_HORIZON, WRONG_ELEVATION }
    data class Fix(val correction: Double? = null, val failure: Failure? = null)
    fun fix(tappedAzimuth: Double, tappedElevation: Double, sun: Position): Fix {
        if (!sun.elevation.isFinite() || sun.elevation <= -0.5) return Fix(failure = Failure.BELOW_HORIZON)
        if (!sun.azimuth.isFinite() || !tappedAzimuth.isFinite() || !tappedElevation.isFinite() || abs(tappedElevation - sun.elevation) > 6) return Fix(failure = Failure.WRONG_ELEVATION)
        return Fix(correction = GeoMath.difference(sun.azimuth, tappedAzimuth))
    }
    fun at(seconds: Double, point: GeoPoint): Position? {
        if (!seconds.isFinite() || !point.valid) return null
        val t = (seconds / 86400 + 2440587.5 - 2451545) / 36525
        val longitude = GeoMath.normalize(280.46646 + t * (36000.76983 + t * 0.0003032))
        val anomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val m = Math.toRadians(anomaly)
        val centre = sin(m) * (1.914602 - t * (0.004817 + t * 0.000014)) + sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparent = Math.toRadians(longitude + centre - 0.00569 - 0.00478 * sin(omega))
        val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(apparent))
        val y = tan(obliquity / 2).pow(2)
        val l0 = Math.toRadians(longitude)
        val equation = 4 * Math.toDegrees(y * sin(2 * l0) - 2 * eccentricity * sin(m) + 4 * eccentricity * y * sin(m) * cos(2 * l0) - 0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * m))
        val solar = ((seconds % 86400) / 60 + equation + 4 * point.longitude) % 1440
        val hour = Math.toRadians((solar / 4 - 180).let { if (it < -180) it + 360 else it })
        val latitude = Math.toRadians(point.latitude)
        val zenith = acos((sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hour)).coerceIn(-1.0, 1.0))
        val elevation = 90 - Math.toDegrees(zenith)
        val azimuth = GeoMath.normalize(Math.toDegrees(atan2(sin(hour), cos(hour) * sin(latitude) - tan(declination) * cos(latitude))) + 180)
        val tangent = tan(Math.toRadians(elevation))
        val refraction = when {
            elevation > 85 -> 0.0
            elevation > 5 -> (58.1 / tangent - 0.07 / tangent.pow(3) + 0.000086 / tangent.pow(5)) / 3600
            elevation > -0.575 -> (1735 + elevation * (-518.2 + elevation * (103.4 + elevation * (-12.79 + elevation * 0.711)))) / 3600
            else -> -20.772 / tangent / 3600
        }
        return Position(azimuth, elevation + refraction)
    }
}
