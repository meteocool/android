package com.meteocool.experimental

import kotlin.math.*

data class SkyMarker(val point: GeoPoint, val altitude: Double?, val source: Source) {
    enum class Source { MAP, ECHO, ASSUMED_BASE, GROUND }
    val mapSearch get() = "?layer=radar&lat=" + point.latitude + "&lon=" + point.longitude + "&zoom=10&point=" + point.latitude + "," + point.longitude
}

object SkyMarkerRules {
    const val ASSUMED_BASE = 1_500.0
    const val MINIMUM_ELEVATION = 4.0
    const val MAX_RANGE = 180_000.0
    data class Ray(val origin: GeoFrame.Vector, val direction: GeoFrame.Vector)
    data class Echo(val point: GeoFrame.Vector, val radius: Double)

    /** The displayed sampled echo first; otherwise ground or an explicitly assumed cumulus base. */
    fun mark(ray: Ray, frame: GeoFrame, eyeAltitude: Double, echoes: Sequence<Echo>): SkyMarker? {
        val norm = sqrt(ray.direction.dot(ray.direction))
        if (!norm.isFinite() || norm < 1e-9 || !eyeAltitude.isFinite()) return null
        val direction = ray.direction * (1 / norm)
        var nearest = Double.POSITIVE_INFINITY
        for (echo in echoes) {
            if (!echo.radius.isFinite() || echo.radius <= 0) continue
            val difference = echo.point - ray.origin
            val along = difference.dot(direction)
            if (along <= 0) continue
            val miss = max(0.0, difference.dot(difference) - along * along)
            if (miss > echo.radius * echo.radius) continue
            val hit = max(0.0, along - sqrt(echo.radius * echo.radius - miss))
            if (hit < nearest) nearest = hit
        }
        if (nearest <= MAX_RANGE) {
            val position = frame.geodetic(ray.origin + direction * nearest) ?: return null
            return SkyMarker(position.first, position.second, SkyMarker.Source.ECHO)
        }
        val radius = frame.gaussianRadius
        val centre = GeoFrame.Vector(0.0, 0.0, -(radius + eyeAltitude))
        // Keep assumed ground below the eye, including missing altitude (0) and places below sea level.
        val ground = eyeAltitude - 1.6
        val groundHit = intersectSphere(ray.origin, direction, centre, radius + ground)
        if (groundHit != null && groundHit <= MAX_RANGE) {
            val position = frame.geodetic(ray.origin + direction * groundHit) ?: return null
            return SkyMarker(position.first, null, SkyMarker.Source.GROUND)
        }
        val elevation = Math.toDegrees(asin(direction.z.coerceIn(-1.0, 1.0)))
        if (elevation < MINIMUM_ELEVATION) return null
        val base = intersectSphere(ray.origin, direction, centre, radius + ground + ASSUMED_BASE) ?: return null
        if (base > MAX_RANGE) return null
        val position = frame.geodetic(ray.origin + direction * base) ?: return null
        return SkyMarker(position.first, position.second, SkyMarker.Source.ASSUMED_BASE)
    }

    private fun intersectSphere(origin: GeoFrame.Vector, direction: GeoFrame.Vector, centre: GeoFrame.Vector, radius: Double): Double? {
        val toCentre = centre - origin
        val along = toCentre.dot(direction)
        val discriminant = radius * radius - toCentre.dot(toCentre) + along * along
        if (!discriminant.isFinite() || discriminant < 0) return null
        val near = along - sqrt(discriminant)
        val far = along + sqrt(discriminant)
        return when { near > 0.01 -> near; far > 0.01 -> far; else -> null }
    }
}

object StormTracks {
    /** Same 32-segment, major-axis-from-north uncertainty ellipse as the iOS storm scene. */
    fun ellipse(point: GeoPoint, majorMetres: Double, minorMetres: Double, bearing: Double): List<GeoPoint> {
        if (!point.valid || !majorMetres.isFinite() || !minorMetres.isFinite() || !bearing.isFinite() || majorMetres <= 0 || minorMetres <= 0) return emptyList()
        return (0..32).map { i ->
            val angle = i / 32.0 * 2 * PI
            val x = majorMetres * cos(angle)
            val y = minorMetres * sin(angle)
            GeoMath.destination(point, hypot(x, y), bearing + Math.toDegrees(atan2(y, x)))
        }
    }
    fun motionArrow(point: GeoPoint, heading: Double?, speedKmh: Double?): List<List<GeoPoint>> {
        if (heading == null || speedKmh == null || !heading.isFinite() || !speedKmh.isFinite() || speedKmh <= 0) return emptyList()
        val metres = speedKmh * 250 // Fifteen minutes.
        val tip = GeoMath.destination(point, metres, heading)
        return listOf(listOf(point, tip)) + listOf(-150.0, 150.0).map { side ->
            listOf(tip, GeoMath.destination(tip, metres * 0.2, heading + side))
        }
    }
}
