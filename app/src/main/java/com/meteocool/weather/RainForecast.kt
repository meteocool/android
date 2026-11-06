package com.meteocool.weather

import com.google.gson.JsonParser
import kotlin.math.min

/** Shared by rain muting, widgets and the car. Missing radar never means dry. */
data class RainForecast(
    val start: Long,
    val interval: Int,
    val dbz: List<Double?>,
    val observed: Int,
    val threshold: Double,
) {
    sealed interface Phase {
        data class Raining(val end: Long?) : Phase
        data class Approaching(val arrival: Long, val end: Long?) : Phase
        data object Dry : Phase
        data object Unavailable : Phase
    }

    fun at(index: Int): Long = start + index.toLong() * interval

    fun fresh(nowSeconds: Long): Boolean = interval in 1..1800 && observed in 1 until dbz.size &&
        nowSeconds - at(observed - 1) in -60..maxOf(900L, interval * 3L) &&
        at(dbz.lastIndex) > nowSeconds

    fun phase(nowSeconds: Long = System.currentTimeMillis() / 1000): Phase {
        if (!fresh(nowSeconds) || !threshold.isFinite() || threshold < 0) return Phase.Unavailable
        val from = maxOf(observed - 1, ((nowSeconds - start) / interval).toInt()).coerceAtMost(dbz.lastIndex)
        if (dbz.drop(from).any { it == null || !it.isFinite() }) return Phase.Unavailable
        val wet = min(threshold, 14.0)
        val dry = min(threshold, 10.0)
        var began = false
        var dryCount = 0
        for (i in from downTo 0) {
            val value = dbz[i] ?: break
            if (!value.isFinite()) break
            if (value >= threshold) { began = true; break }
            dryCount = if (value < dry) dryCount + 1 else 0
            if (dryCount == 2) break
        }
        val raining = dbz[from]!! >= wet && began
        val arrival = if (raining) from else (from..dbz.lastIndex).firstOrNull { dbz[it]!! >= threshold }
            ?: return Phase.Dry
        var lastWet = arrival
        dryCount = 0
        var end: Long? = null
        for (i in arrival..dbz.lastIndex) {
            if (dbz[i]!! >= wet) lastWet = i
            dryCount = if (dbz[i]!! < dry) dryCount + 1 else 0
            if (dryCount == 2) { end = at(lastWet + 1); break }
        }
        if (end == null && dbz.lastIndex - lastWet >= 2) end = at(lastWet + 1)
        return if (raining) Phase.Raining(end) else Phase.Approaching(at(arrival), end)
    }

    companion object {
        fun fromTimeseries(json: String, threshold: Double): RainForecast? { return try {
            val frames = JsonParser.parseString(json).asJsonObject.getAsJsonObject("frames")
            val entries = frames.entrySet().map { (key, value) ->
                val time = key.toLongOrNull() ?: return null
                if (time !in 1..4_102_444_800L) return null
                time to value
            }.sortedBy { it.first }
            if (entries.size !in 2..1000) return null
            val interval = entries[1].first - entries[0].first
            if (interval !in 1..1800 || entries.zipWithNext().any { (a, b) -> b.first - a.first != interval }) return null
            var observed = 0
            var forecastSeen = false
            val values = entries.mapIndexed { index, (_, frame) ->
                if (frame.isJsonNull) null else {
                    val obj = frame.asJsonObject
                    if (obj.get("source")?.asString == "observation") {
                        if (forecastSeen) return null
                        observed = index + 1
                    } else forecastSeen = true
                    obj.get("dbz")?.takeUnless { it.isJsonNull }?.let {
                        if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) return null
                        it.asDouble.takeIf(Double::isFinite)
                    }
                }
            }
            RainForecast(entries.first().first, interval.toInt(), values, observed, threshold)
        } catch (_: Exception) { null } }
    }
}
