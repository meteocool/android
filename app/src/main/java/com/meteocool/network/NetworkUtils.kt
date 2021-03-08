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

        val test = "http://10.10.4.162:8040/"
        val prod = "https://api.ng.meteocool.com/"

        const val DOC_URL = "https://meteocool.com/documentation.html"
        const val MAP_URL = "https://app.ng.meteocool.com/android.html"
        const val IMPRESS_URL = "https://meteocool.com/#about"
        const val TWITTER_URL = "https://twitter.com/meteocool_de"
        const val GITHUB_URL = "https://github.com/meteocool"

        val POST_CLIENT_DATA = URL(prod + "post_location")
        val POST_CLEAR_NOTIFICATION = URL(prod + "clear_notification")
        val POST_UNREGISTER_TOKEN = URL(prod + "unregister")

    }
}


