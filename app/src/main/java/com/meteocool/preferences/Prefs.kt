package com.meteocool.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.meteocool.location.MeteocoolLocation

/**
 * Every setting the app keeps, in the one preferences file the settings
 * screen writes to. Older versions also wrote to a file named "default";
 * [migrate] folds that into this one.
 */
class Prefs(val sp: SharedPreferences) {

    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context))

    companion object {
        const val NOTIFICATION = "notification"
        const val NOTIFICATION_DETAILS = "notification_details"
        const val NOTIFICATION_INTENSITY = "notification_intensity"
        const val NOTIFICATION_TIME = "notification_time"
        const val MAP_ROTATE = "map_rotate"
        const val MAP_ZOOM = "map_zoom"
        const val BASE_LAYER = "base_layer"
        const val RADAR_COLOR_MAPPING = "radar_color_mapping"
        const val EXPERIMENTAL_FEATURES = "experimental_features"
        const val DEMO_MODE = "demo_mode"
        const val REGISTRATION_ORIGIN = "registration_origin"
        const val PUSH_TOKEN = "push_token"
        const val ONBOARDING_DONE = "onboarding_done"
        private const val LAST_LAT = "last_latitude"
        private const val LAST_LON = "last_longitude"
        private const val LAST_ACCURACY = "last_accuracy"

        /** dBZ thresholds, lowest first: drizzle, light rain, rain, intense rain, hail. */
        val INTENSITIES = listOf(14, 20, 26, 36, 41)
        const val DEFAULT_INTENSITY = 20
        const val DEFAULT_AHEAD = 15
        val BASE_LAYERS = listOf("system", "light", "dark", "osm", "cyclosm")
        val COLOR_MAPPINGS = listOf("classic", "nws", "pyart_stepseq", "homeyer", "lang")

        /** Keys the web map reads through injectSettings(). */
        val WEB_SETTINGS = setOf(MAP_ROTATE, BASE_LAYER, RADAR_COLOR_MAPPING, EXPERIMENTAL_FEATURES)

        /** Keys the push registration carries. */
        val REGISTRATION_SETTINGS = setOf(NOTIFICATION_DETAILS, NOTIFICATION_INTENSITY, NOTIFICATION_TIME)

        /** The backend accepts only 0 < ahead <= 60; the settings offer 5..45 in steps of 5. */
        fun clampAhead(minutes: Int?): Int {
            val m = minutes ?: return DEFAULT_AHEAD
            return (m.coerceIn(5, 45) / 5) * 5
        }

        /** Older builds could store 10 dBZ, which the list never offered. */
        fun clampIntensity(dbz: Int?): Int = if (dbz in INTENSITIES) dbz!! else DEFAULT_INTENSITY
    }

    var notification: Boolean
        get() = sp.getBoolean(NOTIFICATION, false)
        set(value) = sp.edit { putBoolean(NOTIFICATION, value) }

    val withDbz: Boolean get() = sp.getBoolean(NOTIFICATION_DETAILS, false)

    val intensityDbz: Int get() = clampIntensity(sp.getString(NOTIFICATION_INTENSITY, null)?.toIntOrNull())

    val aheadMinutes: Int get() = clampAhead(sp.getString(NOTIFICATION_TIME, null)?.toIntOrNull())

    val mapRotate: Boolean get() = sp.getBoolean(MAP_ROTATE, true)

    var mapZoom: Boolean
        get() = sp.getBoolean(MAP_ZOOM, false)
        set(value) = sp.edit { putBoolean(MAP_ZOOM, value) }

    val baseLayer: String
        get() = sp.getString(BASE_LAYER, null)?.takeIf { it in BASE_LAYERS } ?: "system"

    val radarColorMapping: String
        get() = sp.getString(RADAR_COLOR_MAPPING, null)?.takeIf { it in COLOR_MAPPINGS } ?: "classic"

    var experimentalFeatures: Boolean
        get() = sp.getBoolean(EXPERIMENTAL_FEATURES, false)
        set(value) = sp.edit { putBoolean(EXPERIMENTAL_FEATURES, value) }

    var demoMode: Boolean
        get() = sp.getBoolean(DEMO_MODE, false)
        set(value) = sp.edit { putBoolean(DEMO_MODE, value) }

    /** The API a registration was last posted to, removed once it is unregistered there. */
    var registrationOrigin: String?
        get() = sp.getString(REGISTRATION_ORIGIN, null)
        set(value) = sp.edit { if (value == null) remove(REGISTRATION_ORIGIN) else putString(REGISTRATION_ORIGIN, value) }

    var pushToken: String?
        get() = sp.getString(PUSH_TOKEN, null)?.takeIf { it.isNotEmpty() }
        set(value) = sp.edit { if (value == null) remove(PUSH_TOKEN) else putString(PUSH_TOKEN, value) }

    var onboardingDone: Boolean
        get() = sp.getBoolean(ONBOARDING_DONE, false)
        set(value) = sp.edit { putBoolean(ONBOARDING_DONE, value) }

    fun saveLastLocation(fix: MeteocoolLocation) = sp.edit {
        putFloat(LAST_LAT, fix.latitude.toFloat())
        putFloat(LAST_LON, fix.longitude.toFloat())
        putFloat(LAST_ACCURACY, fix.accuracy)
    }

    /**
     * Folds the old "default" file into this one and repairs stored values
     * that are no longer offered. Safe to run on every launch.
     */
    fun migrate(context: Context) {
        val old = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        if (old.all.isNotEmpty()) {
            sp.edit {
                if (old.getBoolean("is_intro_completed", false)) putBoolean(ONBOARDING_DONE, true)
                old.getString("fb_token", null)
                    ?.takeIf { it != "no token" && it.isNotEmpty() && !sp.contains(PUSH_TOKEN) }
                    ?.let { putString(PUSH_TOKEN, it) }
            }
            old.edit { clear() }
        }
        sp.getString("fb_token", null)?.let { token ->
            sp.edit {
                if (token != "no token" && token.isNotEmpty() && !sp.contains(PUSH_TOKEN)) putString(PUSH_TOKEN, token)
                remove("fb_token")
            }
        }
        sp.edit {
            // The satellite basemap is gone; its successor follows the system theme.
            if (sp.getString(BASE_LAYER, null) == "satellite") putString(BASE_LAYER, "system")
            sp.getString(NOTIFICATION_INTENSITY, null)?.toIntOrNull()?.let {
                if (it !in INTENSITIES) putString(NOTIFICATION_INTENSITY, DEFAULT_INTENSITY.toString())
            }
            // Left behind by the removed location and version bookkeeping.
            listOf("latitude", "longitude", "altitude", "accuracy", "elapsedNanos", "app_version").forEach { remove(it) }
        }
    }
}
