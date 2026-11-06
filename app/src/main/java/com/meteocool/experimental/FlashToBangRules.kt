package com.meteocool.experimental

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracy: Double = 0.0) {
    val valid get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 && accuracy.isFinite()
}

data class Strike(val point: GeoPoint, val time: Double)

object GeoMath {
    const val EARTH_RADIUS = 6_371_008.8
    fun normalize(degrees: Double) = ((degrees % 360) + 360) % 360
    fun difference(a: Double, b: Double) = (normalize(a - b) + 180) % 360 - 180
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val p = Math.toRadians(a.latitude)
        val q = Math.toRadians(b.latitude)
        val h = sin((q - p) / 2).pow(2) + cos(p) * cos(q) * sin(Math.toRadians(b.longitude - a.longitude) / 2).pow(2)
        return 2 * EARTH_RADIUS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val p = Math.toRadians(a.latitude)
        val q = Math.toRadians(b.latitude)
        val d = Math.toRadians(b.longitude - a.longitude)
        return normalize(Math.toDegrees(atan2(sin(d) * cos(q), cos(p) * sin(q) - sin(p) * cos(q) * cos(d))))
    }
    fun destination(a: GeoPoint, metres: Double, bearing: Double): GeoPoint {
        val d = metres / EARTH_RADIUS
        val t = Math.toRadians(bearing)
        val p = Math.toRadians(a.latitude)
        val q = asin(sin(p) * cos(d) + cos(p) * sin(d) * cos(t))
        val lon = a.longitude + Math.toDegrees(atan2(sin(t) * sin(d) * cos(p), cos(d) - sin(p) * sin(q)))
        return GeoPoint(Math.toDegrees(q), (lon + 540) % 360 - 180)
    }
    fun mercator(x: Double, y: Double): GeoPoint? {
        if (!x.isFinite() || !y.isFinite() || abs(x) > 20_037_509 || abs(y) > 20_037_509) return null
        return GeoPoint(Math.toDegrees(atan(sinh(y / 6_378_137))), Math.toDegrees(x / 6_378_137)).takeIf { it.valid }
    }
    fun compass8(degrees: Double) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(normalize(degrees) / 45).roundToInt() % 8]
}

/** Same timing and distance contract as iOS FlashToBangRules. All times are Unix seconds. */
object FlashToBangRules {
    const val SPEED_OF_SOUND = 343.0
    const val MAX_GAP = 60.0
    const val DEBOUNCE = 0.15
    const val SEARCH_FOR = 20.0
    const val KEEP_FOR = 112.5
    const val KEEP_WITHIN = 30_000.0
    const val TICK_WITHIN = 10_000.0
    enum class Quality { CLOSE, FAIR, LOOSE }
    data class Match(val strike: Strike, val distance: Double, val bearing: Double, val offset: Double, val score: Double, val quality: Quality, val candidates: Int)
    data class Measurement(val flash: Double, val bang: Double, val place: GeoPoint, var match: Match? = null, var searching: Boolean = true) {
        val gap get() = bang - flash
        val estimate get() = gap * SPEED_OF_SOUND
        val withinStrikingRange get() = (match?.distance ?: estimate) < 5_000
    }
    fun match(flash: Double, estimate: Double, place: GeoPoint, strikes: Collection<Strike>): Match? {
        if (!flash.isFinite() || !estimate.isFinite() || estimate < 0 || !place.valid) return null
        val tolerance = max(500.0, 0.15 * estimate) + place.accuracy.coerceIn(0.0, 1_000.0)
        val scored = strikes.mapNotNull { strike ->
            val offset = strike.time - flash
            if (!strike.point.valid || !offset.isFinite() || offset !in -2.5..0.5) return@mapNotNull null
            val distance = GeoMath.distance(place, strike.point)
            val residual = distance - estimate
            val spatial = if (residual < 0) -residual / (tolerance * 1.5) else residual / tolerance
            if (spatial > 1) return@mapNotNull null
            val score = spatial.pow(2) + ((offset + 0.5) / 1.5).pow(2)
            Match(strike, distance, GeoMath.bearing(place, strike.point), offset, score, Quality.CLOSE, 0)
        }.sortedWith(compareBy<Match> { it.score }.thenBy { it.strike.time }.thenBy { it.strike.point.latitude }.thenBy { it.strike.point.longitude })
        val best = scored.firstOrNull() ?: return null
        var quality = if (best.score < 0.35) Quality.CLOSE else if (best.score < 1.2) Quality.FAIR else Quality.LOOSE
        val second = scored.getOrNull(1)
        if (second != null && second.score - best.score < 0.25 && GeoMath.distance(best.strike.point, second.strike.point) > 1_000) {
            quality = if (quality == Quality.CLOSE) Quality.FAIR else Quality.LOOSE
        }
        return best.copy(quality = quality, candidates = scored.size)
    }
    class Session {
        enum class Phase { IDLE, COUNTING, MEASURED, TOO_FAR }
        enum class Tap { FLASH, BANG, IGNORED }
        var phase = Phase.IDLE; private set
        var flash = 0.0; private set
        var measurement: Measurement? = null; private set
        val running get() = phase == Phase.COUNTING || measurement?.searching == true
        fun reset() { phase = Phase.IDLE; measurement = null }
        fun tap(now: Double, place: GeoPoint): Tap {
            if (!now.isFinite() || !place.valid) return Tap.IGNORED
            if (phase == Phase.COUNTING) {
                val gap = now - flash
                if (gap < DEBOUNCE) return Tap.IGNORED
                if (gap <= MAX_GAP) {
                    measurement = Measurement(flash, now, place)
                    phase = Phase.MEASURED
                    return Tap.BANG
                }
            }
            flash = now
            measurement = null
            phase = Phase.COUNTING
            return Tap.FLASH
        }
        fun update(now: Double, strikes: Collection<Strike>) {
            if (!now.isFinite()) return
            if (phase == Phase.COUNTING && now - flash > MAX_GAP) phase = Phase.TOO_FAR
            measurement?.let {
                if (!it.searching) return
                it.match = match(it.flash, it.estimate, it.place, strikes) ?: it.match
                if (now - it.bang >= SEARCH_FOR) it.searching = false
            }
        }
    }
}
