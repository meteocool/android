package com.meteocool.firebase

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meteocool.app.app
import com.meteocool.notifications.Notifications
import com.meteocool.notifications.AlertHistory
import com.meteocool.notifications.ReceivedAlert
import com.meteocool.notifications.AlertMute
import kotlinx.coroutines.launch

/**
 * Receives rain alerts and clear messages. New registrations request data
 * alerts so history and muting also work while the app is in the background.
 * Older servers still send FCM notification messages.
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
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: return
        if (!app.prefs.notification) return
        AlertHistory.add(this, ReceivedAlert(message.messageId ?: java.util.UUID.randomUUID().toString(),
            title, body, System.currentTimeMillis()))
        if (AlertMute.active(app.prefs) != null) return
        if (appOnScreen()) {
            // The user is already looking at the weather. The alert still
            // counts as seen: the server sends the next one only after that.
            app.registration.acknowledge("foreground")
            return
        }
        val instance = java.util.UUID.randomUUID().toString()
        AlertNotification.show(this, title, body, instance)
        AlertPreviewWorker.enqueue(this, message.data, instance)
    }

    /** One of the app's screens is in front, not merely visible behind something. */
    private fun appOnScreen(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}
