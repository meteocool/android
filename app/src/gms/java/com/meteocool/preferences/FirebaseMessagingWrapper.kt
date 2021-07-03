package com.meteocool.preferences

import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging


class FirebaseMessagingWrapper {

    companion object {

        fun getFirebaseToken(): String {
        var token = "no token"
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (task.isSuccessful) {
                    token = task.result
                }
            })
        return token
        }
    }
}
