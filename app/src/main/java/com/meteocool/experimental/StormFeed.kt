package com.meteocool.experimental

import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.network.ApiClient
import com.meteocool.network.UserAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal class StormFeed(private val api: ApiClient = ApiClient()) {
    private val environment = MeteocoolEnvironment.current
    private val apiBase = MeteocoolEnvironment.currentApiBase
    private val webBase = environment.webHost + "/"
    private val active get() = environment == MeteocoolEnvironment.STAGING && MeteocoolEnvironment.current == environment && MeteocoolEnvironment.currentApiBase == apiBase

    suspend fun strikes(point: GeoPoint, now: Double): List<Strike>? {
        if (!active) return null
        val result = api.request(apiBase, "v3/lightning/baseline?baseline=${(now - FlashToBangRules.KEEP_FOR).toLong()}") ?: return null
        if (!active || result.status !in 200..299) return null
        return StormData.strikes(result.body, now, point)
    }
    suspend fun storms(now: Double): List<StormData.Storm>? {
        if (!active) return null
        val result = api.request(webBase, "cells/volumes") ?: return null
        if (!active || result.status !in 200..299) return null
        return StormData.storms(result.body, now)
    }
    suspend fun cells(now: Double): List<StormData.Cell>? {
        if (!active) return null
        val result = api.request(webBase, "cells/current") ?: return null
        if (!active || result.status !in 200..299) return null
        return StormData.cells(result.body, now)
    }
    suspend fun track(code: String): StormData.Track? {
        if (!active || !Regex("^[0-9]{22}$").matches(code)) return null
        val result = api.request(webBase, "cells/tracks/" + code) ?: return null
        return if (active && result.status in 200..299) StormData.track(result.body, code) else null
    }
    suspend fun mesocyclones(now: Double): List<StormData.Mesocyclone>? {
        if (!active) return null
        val result = api.request(webBase, "mesocyclones/all/") ?: return null
        return if (active && result.status in 200..299) StormData.mesocyclones(result.body, now) else null
    }
    suspend fun lightning(now: Double): List<Strike>? {
        if (!active) return null
        val result = api.request(webBase, "lightning_cache") ?: return null
        return if (active && result.status in 200..299) StormData.recentLightning(result.body, now) else null
    }
    suspend fun volume(path: String): StormVolume? = withContext(Dispatchers.IO) {
        if (!active || !StormData.validPath(path)) return@withContext null
        // Immutable radar assets are public; use the matching asset host, never a feed-provided URL.
        val url = URL("https://assets-staging.meteocool.com/$path")
        var connection: HttpURLConnection? = null
        try {
            val context = currentCoroutineContext()
            context.ensureActive()
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UserAgent.value)
                setRequestProperty("Accept", "application/octet-stream")
            }
            if (connection.responseCode !in 200..299 || connection.contentLength > StormVolume.MAX_BYTES) return@withContext null
            val bytes = connection.inputStream.use { StormVolume.readBounded(it) { context.ensureActive() } }
            context.ensureActive()
            if (active) StormVolume.decode(bytes) else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
