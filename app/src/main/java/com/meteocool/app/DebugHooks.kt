package com.meteocool.app

import android.content.Intent
import androidx.core.content.edit
import com.meteocool.BuildConfig
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.BackgroundLocationWorker
import com.meteocool.preferences.Prefs

/**
 * Debug builds only: lets a test run point the native API at a local
 * recorder and supply a push token, as the iOS UI tests do with
 * MC_TEST_API_URL. Set through launch extras:
 *
 *     adb shell am start -n com.meteocool/.ui.SplashActivity \
 *         --es mc_test_api_url http://10.0.2.2:18765/ --es mc_test_token aaaa… \
 *         --es mc_test_map_url http://10.0.2.2:5173/android.html
 *
 * `--ez mc_run_background_worker true` runs the background location worker once.
 *
 * An empty value clears it. Release builds ignore all of this.
 */
object DebugHooks {
    private const val EXTRA_API = "mc_test_api_url"
    private const val EXTRA_TOKEN = "mc_test_token"
    private const val EXTRA_MAP = "mc_test_map_url"
    private const val EXTRA_RUN_WORKER = "mc_run_background_worker"
    private const val PREF_API = "debug_test_api_url"
    private const val PREF_TOKEN = "debug_test_token"
    private const val PREF_MAP = "debug_test_map_url"

    fun apply(prefs: Prefs) {
        if (!BuildConfig.DEBUG) return
        MeteocoolEnvironment.testApiOverride = prefs.sp.getString(PREF_API, null)
        MeteocoolEnvironment.testMapOverride = prefs.sp.getString(PREF_MAP, null)
        prefs.sp.getString(PREF_TOKEN, null)?.let { prefs.pushToken = it }
    }

    fun hasTestToken(prefs: Prefs): Boolean = BuildConfig.DEBUG && prefs.sp.contains(PREF_TOKEN)

    /** Reads the launch extras; returns whether anything changed. */
    fun fromIntent(app: MeteocoolApp, intent: Intent?): Boolean {
        if (!BuildConfig.DEBUG || intent == null) return false
        if (intent.getBooleanExtra(EXTRA_RUN_WORKER, false)) BackgroundLocationWorker.runOnce(app)
        val api = intent.getStringExtra(EXTRA_API)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val map = intent.getStringExtra(EXTRA_MAP)
        if (api == null && token == null && map == null) return false
        app.prefs.sp.edit {
            api?.let { if (it.isEmpty()) remove(PREF_API) else putString(PREF_API, it) }
            token?.let { if (it.isEmpty()) remove(PREF_TOKEN) else putString(PREF_TOKEN, it) }
            map?.let { if (it.isEmpty()) remove(PREF_MAP) else putString(PREF_MAP, it) }
        }
        apply(app.prefs)
        token?.takeIf { it.isNotEmpty() }?.let { app.registration.setToken(it) }
        return true
    }
}
