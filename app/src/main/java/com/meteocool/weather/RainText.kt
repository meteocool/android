package com.meteocool.weather

import android.content.Context
import com.meteocool.R
import java.util.Date

object RainText {
    fun summary(context: Context, forecast: RainForecast, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        fun time(seconds: Long) = android.text.format.DateFormat.getTimeFormat(context).format(Date(seconds * 1000))
        return when (val phase = forecast.phase(nowSeconds)) {
            is RainForecast.Phase.Raining -> phase.end?.let { context.getString(R.string.rain_until, time(it)) }
                ?: context.getString(R.string.rain_continues)
            is RainForecast.Phase.Approaching -> context.getString(R.string.rain_starts, time(phase.arrival))
            RainForecast.Phase.Dry -> context.getString(R.string.rain_dry_through, time(forecast.at(forecast.dbz.lastIndex)))
            RainForecast.Phase.Unavailable -> context.getString(R.string.rain_unavailable)
        }
    }
}
