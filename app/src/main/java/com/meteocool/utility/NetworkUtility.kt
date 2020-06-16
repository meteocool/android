package com.meteocool.utility

import android.util.Log
import com.google.gson.Gson
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

class NetworkUtility{
companion object {

    val test = "http://10.10.4.162:8040/"
    val prod = "https://meteocool.com/"

    const val DOC_URL = "https://meteocool.com/documentation.html"
    const val MAP_URL = "https://meteocool.com/?mobile=android2"
    const val FEEDBACK_URL = "https://meteocool.com/#about"
    const val IMPRESSUM_URL = "https://meteocool.com/#impressum"

    val POST_CLIENT_DATA = URL(prod + "post_location")
    val POST_CLEAR_NOTIFICATION  = URL(prod + "clear_notification")
    val POST_UNREGISTER_TOKEN  = URL(prod + "unregister")

    private fun buildJSONString(json : JSON) : String{
        val gsonBuilder = Gson().newBuilder().create()
        val jsonAsString = gsonBuilder.toJson(json)
        Log.d("NetworkUtility", "JSON $jsonAsString")
        return jsonAsString
    }

    fun sendPostRequest(json : JSON, url: URL) {

        val jsonAsString = buildJSONString(json)

        try {
            with(url.openConnection() as HttpURLConnection) {
                // optional default is GET
                requestMethod = "POST"
                setRequestProperty("charset", "utf-8")
                setRequestProperty("Content-lenght", jsonAsString.length.toString())
                setRequestProperty("Content-Type", "application/json")

                val wr = OutputStreamWriter(outputStream)

                wr.write(jsonAsString)
                wr.flush()

                Log.d("NetworkUtility", "URL $url")
                Log.d("NetworkUtility", "HTTP-Response $responseCode")

                BufferedReader(InputStreamReader(inputStream)).use {
                    val response = StringBuffer()

                    var inputLine = it.readLine()
                    while (inputLine != null) {
                        response.append(inputLine)
                        inputLine = it.readLine()
                    }
                    it.close()
                    Log.d("NetworkUtility", "$response")
                }
            }
        }catch(e : Exception){
            Log.d("NetworkUtility", "Upload Error")
        }
    }
}


}


