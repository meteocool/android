package com.meteocool.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.meteocool.location.MeteocoolLocation

/**
 * Every setting the app keeps, in the one preferences file the settings
 * screen writes to. Settings from before 4.0 are not carried over
 * ([resetOldSettings]).
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
        const val ENVIRONMENT = "environment"
        const val REGISTRATION_ORIGIN = "registration_origin"
        const val PUSH_TOKEN = "push_token"
        const val ONBOARDING_DONE = "onboarding_done"
        private const val LAST_LAT = "last_latitude"
        private const val LAST_LON = "last_longitude"
        private const val LAST_ACCURACY = "last_accuracy"
        const val SETTINGS_VERSION = "settings_version"

        /** Settings written by an older version than this are dropped, not migrated. */
        const val CURRENT_SETTINGS_VERSION = 4

        /** dBZ thresholds, lowest first: drizzle, light rain, rain, intense rain, hail. */
        val INTENSITIES = listOf(14, 20, 26, 36, 41)
        const val DEFAULT_INTENSITY = 20
        const val DEFAULT_AHEAD = 15
        val BASE_LAYERS = listOf("system", "light", "dark", "osm", "cyclosm")
        val COLOR_MAPPINGS = listOf("classic", "nws", "pyart_stepseq", "homeyer", "lang")

        /** Keys the web map reads through injectSettings(). A change of Mode reloads the map instead. */
        val WEB_SETTINGS = setOf(MAP_ROTATE, BASE_LAYER, RADAR_COLOR_MAPPING)

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

    val mapRotate: Boolean get() = sp.getBoolean(MAP_ROTATE, false)

    var mapZoom: Boolean
        get() = sp.getBoolean(MAP_ZOOM, false)
        set(value) = sp.edit { putBoolean(MAP_ZOOM, value) }

    val baseLayer: String
        get() = sp.getString(BASE_LAYER, null)?.takeIf { it in BASE_LAYERS } ?: "system"

    val radarColorMapping: String
        get() = sp.getString(RADAR_COLOR_MAPPING, null)?.takeIf { it in COLOR_MAPPINGS } ?: "classic"

    /** The deployment chosen under Mode; read it through MeteocoolEnvironment.current. */
    var environment: String?
        get() = sp.getString(ENVIRONMENT, null)
        set(value) = sp.edit { putString(ENVIRONMENT, value) }

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
     * Drops every setting an older version wrote, and onboarding with them:
     * 4.0 starts over as if freshly installed, then onboarding asks for
     * location and alerts again. [legacy] is the file named "default" that
     * 2.x also wrote to. A fresh install only gets the version stamped.
     *
     * The 2.x push registration is left alone: it lives on a backend that can
     * no longer deliver to Android, and the token goes with the rest.
     *
     * Returns whether there was anything to drop.
     */
    fun resetOldSettings(legacy: SharedPreferences): Boolean {
        if (sp.getInt(SETTINGS_VERSION, 0) >= CURRENT_SETTINGS_VERSION) return false
        val upgraded = sp.all.isNotEmpty() || legacy.all.isNotEmpty()
        legacy.edit(commit = true) { clear() }
        sp.edit(commit = true) {
            clear()
            putInt(SETTINGS_VERSION, CURRENT_SETTINGS_VERSION)
        }
        return upgraded
    }
}
