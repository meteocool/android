package com.meteocool.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationManager
import android.content.Context
import com.meteocool.network.JSONClearPost
import com.meteocool.network.NetworkUtils
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber


class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        SharedPrefUtils.saveFirebaseToken(defaultSharedPreferences, p0)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.d("From: %s", remoteMessage.from!!)
        Timber.d("Notification Message Body: %s", remoteMessage.data["clear_all"])

        if (remoteMessage.data["clear_all"] == "true") {
            cancelNotification()
        }
    }

    private fun cancelNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        val token = SharedPrefUtils.getFirebaseToken(defaultSharedPreferences)
        NetworkUtils.sendPostRequest(
            JSONClearPost(
                token,
                "launch_screen"
            ), NetworkUtils.POST_CLEAR_NOTIFICATION
        )
    }
}
