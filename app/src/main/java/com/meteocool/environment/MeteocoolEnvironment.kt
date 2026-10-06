package com.meteocool.environment

import com.meteocool.BuildConfig
import com.meteocool.preferences.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The deployment the app talks to. The web map and the native API must come
 * from the same one: each frontend build speaks one backend's contract, and a
 * registration posted to the wrong cluster never produces a notification.
 *
 * `app.meteocool.com` belongs to no environment of its own. It is a custom
 * domain of one of core's Workers, which forwards the app's API calls to its
 * own backend, so moving production is a change in core, not an app release.
 */
enum class MeteocoolEnvironment(val key: String, val webHost: String, val apiBase: String) {
    /** "Production" under Mode in Settings, the default. */
    APP("app", "https://app.meteocool.com", "https://app.meteocool.com/"),

    /** "Experimental Features": ng's v4 in the staging namespace, and core's staging build. */
    STAGING("staging", "https://next.meteocool.com", "https://api-next.meteocool.com/"),

    /** "Demo": the staging code replaying a recorded storm as if it were happening now. */
    DEMO("demo", "https://demo.meteocool.com", "https://api-demo.meteocool.com/");

    /** The page the map view loads. The frontend reads `version` to decide which native calls it may make. */
    val mapUrl: String
        get() = "$webHost/android.html?version=${BuildConfig.VERSION_NAME}"

    /** The host name alone, for matching the page's origin. */
    val webHostName: String
        get() = webHost.removePrefix("https://")

    companion object {
        private val selected = MutableStateFlow(APP)

        /**
         * The deployment chosen under Mode. Only [select] changes it, and
         * everything that depends on it follows [changes], so the web map and
         * the native API never end up on different deployments.
         */
        val current: MeteocoolEnvironment get() = selected.value

        /**
         * [current], for following it: the map reloads, and the push
         * registration moves to the new API (removed from the old one first).
         */
        val changes: StateFlow<MeteocoolEnvironment> = selected

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

        fun init(prefs: Prefs) {
            selected.value = stored(prefs)
        }

        /** Switches the whole app to [environment], without a restart. */
        fun select(environment: MeteocoolEnvironment, prefs: Prefs) {
            if (environment == current) return
            prefs.environment = environment.key
            selected.value = environment
        }

        /**
         * The stored deployment, migrating the two switches Mode replaced.
         * Demo carries over, so the launch notice goes on reminding the user.
         * Experimental Features does not: everyone who had it on goes back to
         * production once, and picks it again under Mode if they want it.
         */
        fun stored(prefs: Prefs): MeteocoolEnvironment {
            entries.firstOrNull { it.key == prefs.environment }?.let { return it }
            val environment = if (prefs.legacyDemoMode) DEMO else APP
            prefs.environment = environment.key
            prefs.clearLegacyModeSwitches()
            return environment
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
