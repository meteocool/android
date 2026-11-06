package com.meteocool.stormlog

import android.content.SharedPreferences
import androidx.core.content.edit

/** Consent is bound to this token and deployment. A failed wipe can never enable collection. */
class StormLogConsent(private val preferences: SharedPreferences) {
    companion object {
        const val ENABLED = "storm_log_enabled"
        const val WIPE_PENDING = "storm_log_wipe_pending"
        const val SORT = "storm_log_sort"
        const val CONTEXT = "storm_log_context"
        private const val ENABLE_PENDING = "storm_log_enable_pending"
    }

    fun matches(context: String) = preferences.getString(CONTEXT, null) == context
    fun enabled(context: String) = matches(context) && preferences.getBoolean(ENABLED, false) && !pending(context)
    fun pending(context: String) = matches(context) && preferences.getBoolean(WIPE_PENDING, false)
    fun enabling(context: String) = pending(context) && preferences.getBoolean(ENABLE_PENDING, false)

    fun begin(context: String, enableAfterWipe: Boolean) = preferences.edit(commit = true) {
        putString(CONTEXT, context)
        putBoolean(ENABLED, false)
        putBoolean(WIPE_PENDING, true)
        putBoolean(ENABLE_PENDING, enableAfterWipe)
    }

    fun complete(context: String) {
        if (!pending(context)) return
        val enable = enabling(context)
        preferences.edit(commit = true) {
            putBoolean(WIPE_PENDING, false)
            putBoolean(ENABLE_PENDING, false)
            putBoolean(ENABLED, enable)
        }
    }
}
