package com.meteocool.widgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.service.LocationProviders
import com.meteocool.network.UserAgent
import com.meteocool.permissions.PermUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

object WidgetData {
    fun usableLocation(fix: MeteocoolLocation, nowMillis: Long, maxAgeMillis: Long): Boolean =
        validCoordinate(fix.latitude, fix.longitude) && fix.accuracy.isFinite() && fix.accuracy in 0f..1000f &&
            abs(nowMillis - fix.timeMillis) <= maxAgeMillis

    suspend fun currentLocation(context: Context, car: Boolean = false): MeteocoolLocation? {
        if (!PermUtils.isLocationPermissionGranted(context)) return null
        val maxAge = if (car) 60_000L else 300_000L
        val saved = context.app.prefs.lastLocation(maxAge)?.takeIf { usableLocation(it, System.currentTimeMillis(), maxAge) }
        if (saved != null) return saved
        if (!car && !PermUtils.isBackgroundLocationPermissionGranted(context)) return null
        return withTimeoutOrNull(15_000) { LocationProviders.currentLocation(context) }
            ?.takeIf { usableLocation(it, System.currentTimeMillis(), maxAge) }
            ?.also { context.app.prefs.saveLastLocation(it) }
    }

    /** Decode bounded images from the selected API; never follow a redirect with location data. */
    suspend fun map(context: Context, latitude: Double, longitude: Double, zoom: Double, wide: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        if (!validCoordinate(latitude, longitude)) return@withContext null
        val prefs = context.app.prefs
        val baseLayer = prefs.baseLayer.let {
            if (it != "system") it else if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        }
        val url = Uri.parse(MeteocoolEnvironment.currentApiBase + "v3/preview/og.png").buildUpon()
            .appendQueryParameter("latLonZ", "$latitude,$longitude,$zoom")
            .appendQueryParameter("aspectRatio", if (wide) "wide" else "square").appendQueryParameter("logo", "false")
            .appendQueryParameter("baseLayer", baseLayer).appendQueryParameter("colormap", prefs.radarColorMapping).build()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 20_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", UserAgent.value)
            }
            if (connection.responseCode != 200) return@withContext null
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) return@withContext null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.RGB_565
                while (bounds.outWidth / inSampleSize > 1152 || bounds.outHeight / inSampleSize > 1152) inSampleSize *= 2
            }
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
            val scale = 576f / maxOf(image.width, image.height)
            val scaled = Bitmap.createScaledBitmap(image, (image.width * scale).toInt().coerceAtLeast(1),
                (image.height * scale).toInt().coerceAtLeast(1), true)
            if (scaled !== image) image.recycle()
            scaled
        } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null } finally { connection?.disconnect() }
    }
}
