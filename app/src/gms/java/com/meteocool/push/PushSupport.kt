package com.meteocool.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/** Push through Firebase Cloud Messaging. */
object PushSupport {
    const val available = true

    suspend fun fetchToken(context: Context): String? = try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: Exception) {
        // No google-services.json in this build, or Play services missing.
        Timber.w("No FCM token: ${e.javaClass.simpleName}")
        null
    }
}
