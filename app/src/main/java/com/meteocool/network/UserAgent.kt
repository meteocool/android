package com.meteocool.network

import android.os.Build
import com.meteocool.BuildConfig

/**
 * What the app tells the servers it is: `meteocool/4.0.0 (36; Android 15)`, the version
 * people see, the build and the system it runs on.
 *
 * `HttpURLConnection`'s own user agent names neither the app nor its version, and the web
 * view's reports every phone as Android 10, so without this the backend's dashboards cannot
 * tell which release or which Android anyone runs.
 */
object UserAgent {
    val value: String by lazy {
        format(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.RELEASE ?: "unknown")
    }

    fun format(version: String, build: Int, release: String) = "meteocool/$version ($build; Android $release)"
}
