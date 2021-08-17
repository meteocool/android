package com.meteocool.firebase

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder


class MyFirebaseMessagingService : Service() {

    companion object {
        fun cancelNotification(context: Context, from: String) {
        }
    }

    override fun onCreate() {
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_NOT_STICKY;
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
    }
}
