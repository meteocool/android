package com.meteocool.environment

import com.meteocool.BuildConfig
import com.meteocool.preferences.Prefs

/**
 * The deployment the app talks to. The web map and the native API must come
 * from the same one: each frontend build speaks one backend's contract, and a
 * registration posted to the wrong cluster never produces a notification.
 *
 * `app.meteocool.com` belongs to no environment of its own. It is a custom
 * domain of one of core's Workers, which forwards the app's API calls to its
 * own backend, so moving production is a change in core, not an app release.
 */
enum class MeteocoolEnvironment(val webHost: String, val apiBase: String) {
    APP("https://app.meteocool.com", "https://app.meteocool.com/"),
    STAGING("https://next.meteocool.com", "https://api-next.meteocool.com/"),
    DEMO("https://demo.meteocool.com", "https://api-demo.meteocool.com/");

    /** The page the map view loads. The frontend reads `version` to decide which native calls it may make. */
    val mapUrl: String
        get() = "$webHost/android.html?version=${BuildConfig.VERSION_NAME}"

    /** The host name alone, for matching the page's origin. */
    val webHostName: String
        get() = webHost.removePrefix("https://")

    companion object {
        /**
         * Picked once per process, so changing Experimental Features or Demo
         * Mode cannot split the map and the API before a restart.
         */
        @Volatile
        var current: MeteocoolEnvironment = APP
            private set

        /** Debug builds only: a local API recorder the UI tests point the app at. */
        @Volatile
        var testApiOverride: String? = null

        /** Debug builds only: a local frontend build to load instead of the environment's. */
        @Volatile
        var testMapOverride: String? = null

        /** The page the map view loads. */
        val currentMapUrl: String
            get() = testMapOverride ?: current.mapUrl

        /** The API native requests go to. */
        val currentApiBase: String
            get() = testApiOverride ?: current.apiBase

        fun select(demoMode: Boolean, experimentalFeatures: Boolean): MeteocoolEnvironment = when {
            demoMode -> DEMO
            experimentalFeatures -> STAGING
            else -> APP
        }

        fun init(prefs: Prefs) {
            current = select(prefs.demoMode, prefs.experimentalFeatures)
        }

        /** The one runtime switch: the launch alert's "Disable Demo Mode". */
        fun leaveDemo(prefs: Prefs) {
            prefs.demoMode = false
            current = select(false, prefs.experimentalFeatures)
        }

        /**
         * The API a stored registration origin belongs to, or null if it is
         * not one this app knows. Staging's API used to live on
         * staging.meteocool.com.
         */
        fun apiBaseFor(storedOrigin: String): String? {
            if (storedOrigin == "https://staging.meteocool.com/") return STAGING.apiBase
            return entries.firstOrNull { it.apiBase == storedOrigin }?.apiBase
        }
    }
}
