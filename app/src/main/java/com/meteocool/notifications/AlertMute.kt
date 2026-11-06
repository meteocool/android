package com.meteocool.notifications

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.meteocool.preferences.Prefs
import com.meteocool.weather.RainForecast
import java.util.Calendar

data class AlertMute(
    @SerializedName("length") val length: String,
    @SerializedName("until") val until: Long,
    @SerializedName("rainBegan") val rainBegan: Boolean = false,
) {
    fun following(forecast: RainForecast, now: Long): AlertMute? {
        if (length != "rain") return this
        return when (forecast.phase(now)) {
            RainForecast.Phase.Unavailable -> this
            RainForecast.Phase.Dry -> null
            is RainForecast.Phase.Approaching -> if (rainBegan) null else rain(forecast, now)
            is RainForecast.Phase.Raining -> rain(forecast, now)
        }
    }

    companion object {
        private val gson = Gson()
        fun timed(days: Int, calendar: Calendar = Calendar.getInstance()): AlertMute {
            require(days == 1 || days == 7)
            val end = calendar.clone() as Calendar
            end.add(Calendar.DAY_OF_YEAR, days)
            return AlertMute(if (days == 1) "day" else "week", end.timeInMillis / 1000)
        }
        fun rain(forecast: RainForecast, now: Long): AlertMute? = when (val phase = forecast.phase(now)) {
            is RainForecast.Phase.Raining -> AlertMute("rain", phase.end ?: forecast.at(forecast.dbz.size), true)
            is RainForecast.Phase.Approaching -> AlertMute("rain", phase.end ?: forecast.at(forecast.dbz.size))
            else -> null
        }
        fun active(prefs: Prefs, now: Long = System.currentTimeMillis() / 1000): AlertMute? = try {
            gson.fromJson(prefs.sp.getString(Prefs.ALERT_MUTE, null), AlertMute::class.java)
                ?.takeIf { it.length in setOf("day", "week", "rain") && it.until > now }
        } catch (_: Exception) { null }
        fun save(prefs: Prefs, mute: AlertMute?) {
            prefs.sp.edit().apply {
                if (mute == null) remove(Prefs.ALERT_MUTE) else putString(Prefs.ALERT_MUTE, gson.toJson(mute))
            }.apply()
        }
    }
}
