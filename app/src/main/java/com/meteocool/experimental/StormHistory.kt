package com.meteocool.experimental

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.*

object StormHistory {
    data class Tile(val network: String, val z: Int, val x: Int, val y: Int) {
        val code get() = "T%02d%05d%05d".format(java.util.Locale.ROOT, z, x, y)
        fun contains(point: GeoPoint): Boolean {
            if (!valid || !point.valid || point.latitude !in -85.05112878..85.05112878) return false
            val n = (1 shl z).toDouble()
            val phi = Math.toRadians(point.latitude)
            val column = floor((point.longitude + 180) / 360 * n).toInt().mod(1 shl z)
            val row = floor((1 - ln(tan(phi) + 1 / cos(phi)) / PI) / 2 * n).toInt()
            return column == x && row == y
        }
        val valid get() = network.matches(Regex("[a-z]{2}")) && z in 9..11 && x in 0 until (1 shl z) && y in 0 until (1 shl z)
    }
    private val stamp = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", java.util.Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT)
    fun scan(path: String): Long? = try {
        if (!StormData.validPath(path)) null else LocalDateTime.parse(path.split('/')[2], stamp).toEpochSecond(ZoneOffset.UTC)
    } catch (_: RuntimeException) { null }
    fun earlier(newest: Long) = (1..24).map { newest - it * 300L }
    fun onClock(known: Long, picked: Long): Long {
        val delta = (picked - known) / 300.0
        val rounded = if (delta < 0) -floor(-delta + 0.5) else floor(delta + 0.5)
        return known + rounded.toLong() * 300
    }
    fun tile(path: String): Tile? {
        if (!StormData.validPath(path)) return null
        val match = Regex("(?:([a-z]{2})-)?T(\\d{2})(\\d{5})(\\d{5})\\.mcvx").matchEntire(path.substringAfterLast('/')) ?: return null
        return Tile(match.groupValues[1].ifEmpty { "de" }, match.groupValues[2].toInt(), match.groupValues[3].toInt(), match.groupValues[4].toInt()).takeIf { it.valid }
    }
    fun path(tile: Tile, scan: Long): String? = if (!tile.valid) null else "meteoradar/volumes/" + stamp.format(java.time.Instant.ofEpochSecond(scan).atOffset(ZoneOffset.UTC)) + "/" + tile.network + "-" + tile.code + ".mcvx"
    fun places(storms: List<StormData.Storm>): List<Tile> {
        val seeds = storms.mapNotNull { tile(it.path) }.map { it.copy(z = 9, x = it.x shr (it.z - 9), y = it.y shr (it.z - 9)) }.distinct()
        return seeds.flatMap { seed -> (-1..1).flatMap { dy -> (-1..1).map { dx -> seed.copy(x = seed.x + dx, y = seed.y + dy) } } }
            .filter { it.valid }.distinct().take(36)
    }
    data class Quarter(val tile: Tile, val peak: Double, val columns: Int)
    fun quarters(volume: StormVolume, tile: Tile): List<Quarter> {
        val x0 = volume.apron[0]; val y0 = volume.apron[1]
        val width = volume.nx - 2 * x0; val height = volume.ny - 2 * y0
        return (0..1).flatMap { qy -> (0..1).map { qx ->
            var peak = Double.NEGATIVE_INFINITY
            var count = 0
            val startY = if (qy == 0) height / 2 else 0
            val endY = if (qy == 0) height else height / 2
            for (y in startY until endY) for (x in (if (qx == 0) 0 else width / 2) until (if (qx == 0) width / 2 else width)) {
                var column = Double.NEGATIVE_INFINITY
                for (z in 0 until volume.nz) {
                    val voxel = volume.sample(x + x0, y + y0, z) ?: continue
                    if (voxel.confidence > 0) column = max(column, voxel.dbz)
                }
                peak = max(peak, column)
                if (column >= 25) count++
            }
            Quarter(tile.copy(z = 10, x = tile.x * 2 + qx, y = tile.y * 2 + qy), peak, count)
        } }
    }
    fun asks(quarter: Quarter): Pair<List<Tile>, List<Tile>> {
        if (quarter.columns < 2) return emptyList<Tile>() to emptyList()
        val tile = quarter.tile
        val fine = (0..1).flatMap { dy -> (0..1).map { dx -> tile.copy(z = 11, x = tile.x * 2 + dx, y = tile.y * 2 + dy) } }
        return if (quarter.peak >= 50) fine to emptyList() else listOf(tile) to fine
    }
}

object StormMotion {
    fun minutes(scannedAt: Double, now: Double): Double = if (scannedAt.isFinite() && now.isFinite()) ((now - scannedAt) / 60).coerceIn(0.0, 30.0) else 0.0
    fun advance(point: GeoPoint, heading: Double?, speedKmh: Double?, minutes: Double): GeoPoint {
        if (!point.valid || heading == null || speedKmh == null || !heading.isFinite() || !speedKmh.isFinite() || speedKmh <= 0 || !minutes.isFinite() || minutes <= 0) return point
        return GeoMath.destination(point, speedKmh * 1_000 * minutes.coerceAtMost(30.0) / 60, heading)
    }
}
