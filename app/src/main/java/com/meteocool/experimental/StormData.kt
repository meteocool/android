package com.meteocool.experimental

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal fun JsonObject.number(name: String): Double? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.takeIf { it.isFinite() }
internal fun JsonObject.word(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
internal fun JsonObject.objects(name: String): List<JsonObject>? = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.let { array ->
    if (array.size() > 20_000 || array.any { !it.isJsonObject }) null else array.map { it.asJsonObject }
}
internal fun JsonObject.numbers(name: String): List<Double>? = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.let { array ->
    if (array.size() > 10 || array.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isNumber }) null
    else array.map { it.asDouble }.takeIf { values -> values.all { it.isFinite() } }
}

object StormData {
    data class Storm(val code: String, val point: GeoPoint, val path: String, val system: String, val scannedAt: Double, val tier: Int, val peakDbz: Double?)
    data class ForecastPoint(val point: GeoPoint, val time: Double, val majorKm: Double, val minorKm: Double, val angle: Double)
    data class Track(val code: String, val points: List<GeoPoint>, val place: String?)
    data class Cell(
        val code: String, val point: GeoPoint, val path: String?, val echoTop: Double?, val peakDbz: Double?,
        val heading: Double?, val speedKmh: Double?, val forecast: List<ForecastPoint> = emptyList(),
        val areaKm2: Double? = null, val lightningRate: Int? = null, val hail: Boolean = false,
        val gust: Boolean = false, val heavyRain: Boolean = false, val mesocyclone: Boolean = false,
    )
    /** Cells and volumes are polled independently; a newer cell scan may precede its tile index. */
    fun linkCells(storms: List<Storm>, cells: List<Cell>): Map<String, Cell> {
        val listed = storms.map { it.path }.toSet()
        val result = mutableMapOf<String, Cell>()
        for (cell in cells) {
            val path = cell.path?.takeIf { it in listed }
                ?: storms.firstOrNull { StormHistory.tile(it.path)?.contains(cell.point) == true }?.path
                ?: continue
            val previous = result[path]
            if (previous == null || (cell.peakDbz ?: 0.0) > (previous.peakDbz ?: 0.0)) result[path] = cell
        }
        return result
    }
    fun strongestCell(storm: Storm?, storms: List<Storm>, cells: List<Cell>): Cell? {
        if (storm == null) return null
        val members = storms.filter { it.system == storm.system }.map { it.path }.toSet()
        return linkCells(storms, cells).filterKeys { it in members }.values.maxByOrNull { it.peakDbz ?: 0.0 }
    }
    fun track(json: String, expectedCode: String): Track? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val properties = root.get("properties")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val code = properties.word("code")?.takeIf { it == expectedCode && Regex("^[0-9]{22}$").matches(it) } ?: return null
            val geometry = root.get("geometry")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val coordinates = geometry.get("coordinates")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            val entries = when (geometry.word("type")) {
                "Point" -> listOf(coordinates)
                "LineString" -> {
                    if (coordinates.size() > 4_096 || coordinates.any { !it.isJsonArray }) return null
                    coordinates.map { it.asJsonArray }
                }
                else -> return null
            }
            val points = entries.map { pair ->
                if (pair.size() != 2 || pair.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isNumber }) return null
                GeoPoint(pair[1].asDouble, pair[0].asDouble).takeIf { it.valid } ?: return null
            }
            val place = properties.get("placement")?.takeIf { it.isJsonObject }?.asJsonObject?.word("place")?.take(200)
            Track(code, points, place)
        } catch (_: RuntimeException) { null }
    }
    private fun forecast(entry: JsonObject): List<ForecastPoint> {
        return entry.objects("forecast").orEmpty().take(96).mapNotNull { point ->
            val coordinate = GeoPoint(point.number("lat") ?: return@mapNotNull null, point.number("lon") ?: return@mapNotNull null)
            val time = timestamp(point.word("t")) ?: return@mapNotNull null
            val major = point.number("major_km") ?: 3.0
            val minor = point.number("minor_km") ?: 3.0
            if (!coordinate.valid || major !in 0.01..200.0 || minor !in 0.01..200.0) return@mapNotNull null
            ForecastPoint(coordinate, time, major, minor, point.number("angle_deg") ?: 0.0)
        }
    }
    data class Mesocyclone(val point: GeoPoint, val diameter: Double, val intensity: Int, val time: Double)
    fun mesocyclones(json: String, now: Double): List<Mesocyclone>? {
        return try {
            val array = JsonParser.parseString(json).takeIf { it.isJsonArray }?.asJsonArray ?: return null
            if (array.size() > 10_000) return null
            array.mapNotNull { element ->
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val point = GeoMath.mercator(entry.number("lon") ?: return@mapNotNull null, entry.number("lat") ?: return@mapNotNull null) ?: return@mapNotNull null
                val time = (entry.number("time") ?: return@mapNotNull null) / 1_000
                if (!fresh(time, now)) return@mapNotNull null
                Mesocyclone(point, (entry.number("diameter") ?: 2_000.0).coerceIn(0.0, 100_000.0), (entry.number("intensity") ?: 0.0).toInt(), time)
            }
        } catch (_: RuntimeException) { null }
    }
    fun recentLightning(json: String, now: Double): List<Strike>? {
        return try {
            val array = JsonParser.parseString(json).takeIf { it.isJsonArray }?.asJsonArray ?: return null
            if (array.size() > 100_000) return null
            array.mapNotNull { element ->
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val time = (entry.number("time") ?: return@mapNotNull null) / 1_000
                val point = GeoMath.mercator(entry.number("lon") ?: return@mapNotNull null, entry.number("lat") ?: return@mapNotNull null) ?: return@mapNotNull null
                if (now - time !in -60.0..600.0) null else Strike(point, time)
            }
        } catch (_: RuntimeException) { null }
    }
    private val volumePath = Regex("^meteoradar/volumes/(\\d{8}T\\d{6})/((?:[a-z]{2}-)?(?:T\\d{12}|G\\d{10}|R\\d{1,12}))\\.mcvx$")
    fun validTarget(value: String?) = value != null && (Regex("^[0-9]{22}$").matches(value) || volumePath.matches(value))
    fun validPath(value: String) = volumePath.matches(value)
    fun mapSearch(storm: Storm, cell: Cell? = null): String? {
        if (cell != null && Regex("^[0-9]{22}$").matches(cell.code)) return "?layer=cells3d&cell=${cell.code}"
        val match = volumePath.matchEntire(storm.path) ?: return null
        return "?layer=cells3d&cloud=${match.groupValues[1]}/${match.groupValues[2]}"
    }
    fun timestamp(value: String?): Double? {
        if (value == null || value.length !in 19..35) return null
        return try { Instant.parse(value).toEpochMilli() / 1_000.0 }
        catch (_: DateTimeParseException) {
            // Older BSON timestamps omit their UTC offset.
            try { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() / 1_000.0 }
            catch (_: DateTimeParseException) { null }
        }
    }
    fun fresh(at: Double, now: Double) = at.isFinite() && now.isFinite() && now - at in -60.0..1_200.0
    fun storms(json: String, now: Double): List<Storm>? { return try {
        val root = JsonParser.parseString(json).asJsonObject
        val reference = timestamp(root.word("reference_time")) ?: return null
        if (!fresh(reference, now)) return null
        val entries = root.objects("volumes") ?: return null
        entries.mapNotNull { entry ->
            val path = entry.word("path") ?: return@mapNotNull null
            val code = entry.word("code") ?: return@mapNotNull null
            val point = GeoPoint(entry.number("lat") ?: return@mapNotNull null, entry.number("lon") ?: return@mapNotNull null)
            val date = timestamp(entry.word("scanned_at")) ?: timestamp(entry.word("reference_time")) ?: reference
            if (!validPath(path) || !point.valid || !fresh(date, now) || entry.get("coarse")?.toString() == "true") return@mapNotNull null
            Storm(code, point, path, entry.word("system") ?: path, date, entry.number("tier")?.toInt() ?: 2, entry.number("peak_dbz"))
        }.takeIf { entries.isEmpty() || it.isNotEmpty() }
    } catch (_: RuntimeException) { null } }
    fun cells(json: String, now: Double): List<Cell>? { return try {
        val root = JsonParser.parseString(json).asJsonObject
        val reference = timestamp(root.word("reference_time")) ?: return null
        if (!fresh(reference, now)) return null
        root.objects("cells")?.mapNotNull { entry ->
            val code = entry.word("code")?.takeIf { Regex("^[0-9]{22}$").matches(it) } ?: return@mapNotNull null
            val point = GeoPoint(entry.number("lat") ?: return@mapNotNull null, entry.number("lon") ?: return@mapNotNull null)
            if (!point.valid || !fresh(timestamp(entry.word("t")) ?: reference, now)) return@mapNotNull null
            val volume = entry.get("volume")?.takeIf { it.isJsonObject }?.asJsonObject?.word("path")?.takeIf { validPath(it) }
            Cell(code, point, volume, entry.number("echo_top_m"), entry.number("max_dbz"), entry.number("heading_deg"), entry.number("speed_kmh"),
                forecast(entry), entry.number("area_km2"), entry.number("lightning_rate")?.takeIf { it >= 0 }?.toInt(),
                (entry.number("hail_flag") ?: 0.0) > 0, (entry.number("gust_flag") ?: 0.0) > 0,
                (entry.number("heavy_rain_flag") ?: 0.0) > 0, (entry.number("meso_severity") ?: 0.0) > 0)
        }
    } catch (_: RuntimeException) { null } }
    /** API backfill carries complete timestamps in milliseconds; never use rounded cache times for matching. */
    fun strikes(json: String, now: Double, place: GeoPoint): List<Strike>? { return try {
        val root = JsonParser.parseString(json).asJsonObject
        root.objects("strikes")?.mapNotNull { entry ->
            val time = (entry.number("time_wall") ?: return@mapNotNull null) / 1_000
            val point = GeoMath.mercator(entry.number("lon") ?: return@mapNotNull null, entry.number("lat") ?: return@mapNotNull null) ?: return@mapNotNull null
            if (now - time !in -1.0..FlashToBangRules.KEEP_FOR || GeoMath.distance(place, point) > FlashToBangRules.KEEP_WITHIN) null else Strike(point, time)
        }
    } catch (_: RuntimeException) { null } }
}
