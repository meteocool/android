package com.meteocool.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * POSTs JSON to the meteocool API.
 *
 * The v4 backend answers a payload it rejects with `200 {"success": false}`
 * rather than a 4xx, so a request only succeeded if the body says so. Logs
 * carry the status only, never the payload, which holds the location.
 */
class ApiClient(private val timeoutMillis: Int = 20_000) {

    enum class Endpoint(val path: String) {
        POST_LOCATION("post_location"),
        CLEAR_NOTIFICATION("clear_notification"),
        UNREGISTER("unregister"),
    }

    companion object {
        private val gson = Gson()

        /** The JSON body, or null if a number in it is NaN or infinite. */
        fun encode(body: Map<String, Any?>): String? {
            val finite = body.values.all { value ->
                when (value) {
                    is Double -> value.isFinite()
                    is Float -> value.isFinite()
                    else -> true
                }
            }
            return if (finite) gson.toJson(body) else null
        }

        /**
         * Whether a response means the request took effect. Unregistering a
         * token the backend never had counts too: it is not registered either way.
         */
        fun isSuccess(endpoint: Endpoint, status: Int, body: String?): Boolean {
            if (status !in 200..299 || body == null) return false
            val json: JsonObject = try {
                JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return false
            } catch (e: Exception) {
                return false
            }
            val success = json.get("success")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
            if (success == true) return true
            if (endpoint == Endpoint.UNREGISTER) {
                val message = json.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                return message == "not registered"
            }
            return false
        }
    }

    /** Sends [body] to [endpoint] below [apiBase], returning whether it succeeded. */
    suspend fun post(apiBase: String, endpoint: Endpoint, body: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        val json = encode(body) ?: run {
            Timber.w("${endpoint.path}: payload has a non-finite number, not sent")
            return@withContext false
        }
        val url = URL(apiBase + endpoint.path)
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val bytes = json.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }
            val ok = isSuccess(endpoint, status, response)
            Timber.i("${endpoint.path}: HTTP $status${if (ok) "" else ", not accepted"}")
            ok
        } catch (e: Exception) {
            Timber.w("${endpoint.path}: ${e.javaClass.simpleName}")
            false
        } finally {
            connection?.disconnect()
        }
    }
}
