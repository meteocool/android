package com.meteocool.notifications

import com.meteocool.location.MeteocoolLocation
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** What a registration carries besides the location. */
data class RegistrationSettings(
    val token: String,
    val aheadMinutes: Int,
    val intensityDbz: Int,
    val withDbz: Boolean,
    val experimental: Boolean,
    val lang: String,
)

/**
 * The pure rules behind a push registration, kept apart from Android so the
 * backend contract can be tested on the JVM.
 */
object Registration {

    /** A fix older than this is not sent: the backend would alert for where the phone was. */
    const val MAX_FIX_AGE_NANOS = 300L * 1_000_000_000L

    /** The backend's `Language` enum only has de and en; anything else drops the registration. */
    fun lang(localeLanguage: String): String = if (localeLanguage.lowercase().startsWith("de")) "de" else "en"

    /** The backend rejects tokens outside 32..192 characters. */
    fun isValidToken(token: String?): Boolean = token != null && token.length in 32..192

    fun isFresh(fix: MeteocoolLocation, nowElapsedNanos: Long): Boolean =
        fix.accuracy >= 0 && nowElapsedNanos - fix.elapsedRealtimeNanos in 0 until MAX_FIX_AGE_NANOS

    /**
     * Whether a foreground fix is worth another post: it is more accurate, or
     * the phone has moved more than 500 m and a minute has passed.
     */
    fun isSignificant(fix: MeteocoolLocation, lastPosted: MeteocoolLocation?): Boolean {
        if (lastPosted == null) return true
        if (fix.accuracy >= 0 && (lastPosted.accuracy < 0 || fix.accuracy < lastPosted.accuracy)) return true
        if (fix.verticalAccuracy >= 0 &&
            (lastPosted.verticalAccuracy < 0 || lastPosted.verticalAccuracy - fix.verticalAccuracy > 1f)
        ) return true
        val elapsed = fix.elapsedRealtimeNanos - lastPosted.elapsedRealtimeNanos
        return distanceMeters(fix, lastPosted) > 500 && elapsed >= 60L * 1_000_000_000L
    }

    fun distanceMeters(a: MeteocoolLocation, b: MeteocoolLocation): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * The `post_location` body. `details` is what the old backend reads and
     * `withDBZ` what the v4 backend reads; each ignores the other.
     */
    fun payload(fix: MeteocoolLocation, pressureHpa: Float, settings: RegistrationSettings): Map<String, Any> = mapOf(
        "lat" to fix.latitude,
        "lon" to fix.longitude,
        "altitude" to fix.altitude,
        "accuracy" to fix.accuracy.toDouble(),
        "verticalAccuracy" to fix.verticalAccuracy.toDouble(),
        "speed" to fix.speed.toDouble(),
        "course" to fix.course.toDouble(),
        "pressure" to pressureHpa.toDouble(),
        "timestamp" to fix.timeMillis / 1000.0,
        "token" to settings.token,
        "source" to "android",
        "ahead" to settings.aheadMinutes,
        "intensity" to settings.intensityDbz,
        "details" to settings.withDbz,
        "withDBZ" to settings.withDbz,
        "experimental" to settings.experimental,
        "lang" to settings.lang,
    )
}
