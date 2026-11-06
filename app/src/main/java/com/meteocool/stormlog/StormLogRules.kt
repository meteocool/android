package com.meteocool.stormlog

import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.hypot

data class StormPoint(val latitude: Double, val longitude: Double)

data class StormPassage(
    val id: String,
    val placeName: String?,
    val latitude: Double,
    val longitude: Double,
    val cellDegrees: Double?,
    val startedAt: Long,
    val endedAt: Long?,
    val closestAt: Long,
    val over: Boolean,
    val closestKm: Double,
    val peakDbz: Double?,
    val topKm: Double?,
    val vil: Double?,
    val severity: Int,
    val hail: Boolean,
    val meso: Boolean,
    val strikes: Int,
    val track: List<StormPoint>,
    val shareUrl: String,
) {
    fun duration(now: Long) = ((endedAt ?: now) - startedAt).coerceAtLeast(0)
}

enum class StormSort { RECENT, SEVERITY, CLOSEST, DURATION, LIGHTNING }

/** Strict wire validation; no log entries or coordinates are persisted on the phone. */
object StormLogRules {
    const val PATH = "v4/storm-log"
    const val TOKEN_HEADER = "X-Push-Token"
    const val LIMIT = 200
    private val hosts = setOf("app.meteocool.com", "next.meteocool.com", "demo.meteocool.com")
    private val timestamp = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})?$")

    fun context(apiBase: String, token: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$apiBase\n$token".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun collectionEnabled(prefs: SharedPreferences, apiBase: String, token: String): Boolean =
        StormLogConsent(prefs).enabled(context(apiBase, token))

    private fun time(text: String): Long {
        val parts = requireNotNull(timestamp.matchEntire(text))
        val fraction = parts.groupValues[2].padEnd(3, '0').take(3)
        val zone = parts.groupValues[3].ifEmpty { "Z" }.replace("Z", "+0000").replace(":", "")
        val normalized = "${parts.groupValues[1]}.$fraction$zone"
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        return requireNotNull(parser.parse(normalized, position)).also { require(position.index == normalized.length) }.time
    }

    fun decode(body: String): List<StormPassage>? = try {
        require(body.length <= 2_000_000)
        val entries = JsonParser.parseString(body).asJsonObject.getAsJsonArray("entries")
        require(entries.size() <= LIMIT)
        entries.map { item ->
            val j = item.asJsonObject
            val started = time(j.string("started_at"))
            val ended = j.optionalString("ended_at")?.let(::time)
            require(ended == null || ended >= started)
            val latitude = j.number("lat").also { require(it in -90.0..90.0) }
            val longitude = j.number("lon").also { require(it in -180.0..180.0) }
            val coordinates = j.getAsJsonObject("track").getAsJsonArray("coordinates")
            require(coordinates.size() <= 288)
            val track = coordinates.map { position ->
                val p = position.asJsonArray
                require(p.size() >= 2 && p[0].asJsonPrimitive.isNumber && p[1].asJsonPrimitive.isNumber)
                val lon = p[0].asDouble
                val lat = p[1].asDouble
                require(lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0)
                StormPoint(lat, lon)
            }
            StormPassage(
                id = j.string("id").also { require(it.isNotBlank() && it.length <= 256) },
                placeName = j.optionalString("place_name")?.take(200),
                latitude = latitude, longitude = longitude,
                cellDegrees = j.optionalNumber("cell_deg")?.takeIf { it > 0 && it <= 1 },
                startedAt = started, endedAt = ended, closestAt = time(j.string("closest_at")),
                over = j.boolean("over"), closestKm = j.number("closest_km").also { require(it >= 0) },
                peakDbz = j.optionalNumber("peak_dbz"), topKm = j.optionalNumber("top_km"), vil = j.optionalNumber("vil_kg_m2"),
                severity = j.integer("severity").coerceIn(0, 3), hail = j.boolean("hail"), meso = j.boolean("meso"),
                strikes = j.integer("strikes").also { require(it >= 0) }, track = track, shareUrl = j.string("share_url"),
            )
        }.also { records -> require(records.map { it.id }.distinct().size == records.size) }
    } catch (_: Exception) { null }

    fun deleted(status: Int, body: String): Boolean = try {
        status in 200..299 && JsonParser.parseString(body).asJsonObject.boolean("success")
    } catch (_: Exception) { false }

    fun sorted(entries: List<StormPassage>, sort: StormSort, now: Long = System.currentTimeMillis()): List<StormPassage> {
        val primary: Comparator<StormPassage> = when (sort) {
            StormSort.RECENT -> compareBy { it.endedAt != null }
            StormSort.SEVERITY -> compareByDescending<StormPassage> { it.severity }
                .thenByDescending { it.peakDbz ?: Double.NEGATIVE_INFINITY }
            StormSort.CLOSEST -> compareBy { if (it.over) -1.0 else it.closestKm }
            StormSort.DURATION -> compareByDescending { it.duration(now) }
            StormSort.LIGHTNING -> compareByDescending { it.strikes }
        }
        return entries.sortedWith(primary.thenByDescending { it.startedAt }.thenBy { it.id })
    }

    fun approximateRadius(entry: StormPassage): Double {
        val north = (entry.cellDegrees ?: 0.1) * 111_195 / 2
        return hypot(north, north * cos(Math.toRadians(entry.latitude)))
    }

    /** Share only the storm centroid and frame, never a token, place name or arbitrary backend query. */
    fun shareUrl(given: String, mapOrigin: String): String? = try {
        val origin = URI(mapOrigin)
        require(origin.scheme == "https" && origin.host in hosts && origin.port == -1 && origin.userInfo == null)
        val source = URI(given)
        require(source.scheme == "https" && source.host != null && source.userInfo == null)
        val query = requireNotNull(source.rawQuery).split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            require(pair.size == 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        val view = requireNotNull(query["latLonZ"]).split(',').map { it.toDouble() }
        require(view.size == 3 && view.all { it.isFinite() })
        require(view[0] in -90.0..90.0 && view[1] in -180.0..180.0 && view[2] in 0.0..22.0)
        val frame = requireNotNull(query["t"])
        require(Regex("\\d{8}T\\d{4}Z").matches(frame))
        val date = SimpleDateFormat("yyyyMMdd'T'HHmm'Z'", Locale.ROOT).apply { isLenient = false }
        requireNotNull(date.parse(frame, ParsePosition(0)))
        "https://${origin.host}/?layer=radar&latLonZ=${view.joinToString(",")}&overlays=cells,lightning&t=$frame&shared=$frame"
    } catch (_: Exception) { null }

    private fun JsonObject.string(key: String): String {
        val primitive = get(key).asJsonPrimitive
        require(primitive.isString)
        return primitive.asString
    }
    private fun JsonObject.optionalString(key: String): String? = if (get(key) == null || get(key).isJsonNull) null else string(key)
    private fun JsonObject.number(key: String): Double {
        val primitive = get(key).asJsonPrimitive
        require(primitive.isNumber)
        return primitive.asDouble.also { require(it.isFinite()) }
    }
    private fun JsonObject.optionalNumber(key: String): Double? = if (get(key) == null || get(key).isJsonNull) null else number(key)
    private fun JsonObject.integer(key: String): Int = number(key).let { require(it == it.toInt().toDouble()); it.toInt() }
    private fun JsonObject.boolean(key: String): Boolean {
        val primitive = get(key).asJsonPrimitive
        require(primitive.isBoolean)
        return primitive.asBoolean
    }
}
