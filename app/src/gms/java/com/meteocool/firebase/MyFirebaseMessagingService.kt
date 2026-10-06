package com.meteocool.firebase

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meteocool.app.app
import com.meteocool.notifications.Notifications
import kotlinx.coroutines.launch

/**
 * Receives FCM messages. The backend sends two kinds: a notification with
 * title and body when rain is coming, and a data message `clear_all` when it
 * has stopped. FCM shows the first by itself while the app is in the
 * background; only messages that arrive while it is open land here.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Called on a worker thread; the registration lives on the main one.
        app.scope.launch { app.registration.setToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["clear_all"] == "true") {
            Notifications.clearAll(this)
            app.registration.acknowledge("push")
            return
        }
        val notification = message.notification ?: return
        if (!app.prefs.notification) return
        if (appOnScreen()) {
            // The user is already looking at the weather. The alert still
            // counts as seen: the server sends the next one only after that.
            app.registration.acknowledge("foreground")
            return
        }
        AlertNotification.show(this, notification.title, notification.body)
    }

    /** One of the app's screens is in front, not merely visible behind something. */
    private fun appOnScreen(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}
