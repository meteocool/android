package com.meteocool.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.meteocool.network.NetworkUtils
import com.meteocool.network.UploadWorker
import com.meteocool.preferences.SharedPrefUtils
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber


class MyFirebaseMessagingService : FirebaseMessagingService() {

    private var lastPushTime : Long = 0

    companion object{
        fun cancelNotification(context: Context, from : String) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
            val data = UploadWorker.createInputData(mapOf(
                Pair("url",  NetworkUtils.POST_CLEAR_NOTIFICATION.toString()),
                Pair("token", SharedPrefUtils.getFirebaseToken(context.defaultSharedPreferences)),
                Pair("from", from),
            ))
            WorkManager.getInstance(context)
                .enqueue(UploadWorker.createRequest(data))
                .result
        }
    }

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        SharedPrefUtils.saveFirebaseToken(defaultSharedPreferences, p0)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.d("From: %s", remoteMessage.from!!)
        Timber.d("Notification Message Body: %s", remoteMessage.data["clear_all"])
        if (remoteMessage.sentTime - lastPushTime  >= 10000 && remoteMessage.data["clear_all"] == "true") {
            cancelNotification(this, "push")
        }
        lastPushTime = remoteMessage.sentTime
    }
}
