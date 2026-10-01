package com.meteocool.push

import android.content.Context

/** F-Droid builds carry no Firebase, so they cannot receive rain alerts. */
object PushSupport {
    const val available = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchToken(context: Context): String? = null
}
