package com.meteocool.weather

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.network.ApiClient
import com.meteocool.notifications.Registration
import java.util.Locale

object WeatherData {
    suspend fun forecast(
        context: Context, latitude: Double, longitude: Double,
        threshold: Double = context.app.prefs.intensityDbz.toDouble(),
    ): RainForecast? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val origin = MeteocoolEnvironment.currentApiBase
        val language = Registration.lang(ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language ?: "en")
        val path = String.format(Locale.ROOT,
            "v3/radar/timeseries?lat=%.4f&lon=%.4f&threshold=%g&lang=%s&details=%s",
            latitude, longitude, threshold, language, context.app.prefs.withDbz)
        val response = ApiClient(12_000).request(origin, path) ?: return null
        if (response.status != 200 || origin != MeteocoolEnvironment.currentApiBase) return null
        return RainForecast.fromTimeseries(response.body, threshold)
    }
}
