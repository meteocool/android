package com.meteocool.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationManager
import android.content.Context
import com.meteocool.utility.JSONClearPost
import com.meteocool.utility.NetworkUtility
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber


class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        defaultSharedPreferences.edit().putString("fb_token", p0).apply()
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
        val token = defaultSharedPreferences.getString("fb_token", "no token")!!
        NetworkUtility.sendPostRequest(
            JSONClearPost(
                token,
                "launch_screen"
            ), NetworkUtility.POST_CLEAR_NOTIFICATION
        )
    }
}
