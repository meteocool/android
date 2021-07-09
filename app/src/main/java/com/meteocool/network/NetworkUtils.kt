package com.meteocool.network

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import com.google.gson.Gson
import timber.log.Timber
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper class for handling network communication.
 */
class NetworkUtils {
    companion object {

        private const val test = "http://better.meteocool.com"
        private const val prod = "https://api.ng.meteocool.com/"

        const val MAP_URL = "https://app.ng.meteocool.com/android.html?"
        const val PRIVACY_URL = "https://app.ng.meteocool.com/privacy.html"
        const val TWITTER_URL = "https://twitter.com/meteocool_app"
        const val GITHUB_URL = "https://github.com/meteocool"

        val POST_CLIENT_DATA = URL(prod + "post_location")
        val POST_CLEAR_NOTIFICATION = URL(prod + "clear_notification")
        val POST_UNREGISTER_TOKEN = URL(prod + "unregister")


    }
}


