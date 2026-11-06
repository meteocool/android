package com.meteocool.experimental

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import kotlin.math.*

/** The same bounded MCVX v1 radar grid as core and iOS; no synthetic storm geometry. */
class StormVolume private constructor(
    val point: GeoPoint, val nx: Int, val ny: Int, val nz: Int,
    val step: List<Double>, val origin: List<Double>, val floor: Double, val scale: Double,
    val apron: List<Int>, private val bytes: ByteArray, val scannedAt: Double?, val tier: Int,
) {
    data class Voxel(val east: Double, val north: Double, val altitude: Double, val dbz: Double, val confidence: Double)
    fun sample(x: Int, y: Int, z: Int): Voxel? {
        if (x !in apron[0] until nx - apron[0] || y !in apron[1] until ny - apron[1] || z !in apron[2] until nz - apron[2]) return null
        val i = ((z * ny + y) * nx + x) * 2
        val confidence = (bytes[i + 1].toInt() and 255) / 255.0
        val dbz = (bytes[i].toInt() and 255) / scale + floor
        return Voxel(origin[0] + (x + 0.5) * step[0], origin[1] + (y + 0.5) * step[1], origin[2] + (z + 0.5) * step[2], dbz, confidence)
    }
    val coreDbz: Double by lazy {
        val histogram = IntArray(256)
        for (i in bytes.indices step 2) if ((bytes[i + 1].toInt() and 255) >= 128) histogram[bytes[i].toInt() and 255]++
        var remaining = 300
        var index = 255
        while (index > 0) { remaining -= histogram[index]; if (remaining <= 0) break; index-- }
        max(20.0, index / scale + floor - 14)
    }
    /** Strongest sample per block preserves small intense cores when reducing a grid for the camera. */
    fun points(limit: Int = 18_000): List<Voxel> {
        require(limit > 0)
        val stride = max(1, ceil((nx.toDouble() * ny * nz / limit).pow(1.0 / 3)).toInt())
        val points = ArrayList<Voxel>()
        for (z in apron[2] until nz - apron[2] step stride) for (y in apron[1] until ny - apron[1] step stride) for (x in apron[0] until nx - apron[0] step stride) {
            var best: Voxel? = null
            for (zz in z until min(z + stride, nz - apron[2])) for (yy in y until min(y + stride, ny - apron[1])) for (xx in x until min(x + stride, nx - apron[0])) {
                val value = sample(xx, yy, zz) ?: continue
                if (value.confidence > 0.3 && value.dbz >= 20 && (best == null || value.dbz > best.dbz)) best = value
            }
            best?.let(points::add)
        }
        return points
    }
    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        internal fun readBounded(stream: InputStream, limit: Int = MAX_BYTES, check: () -> Unit = {}): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                check()
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= limit) { "Volume too large" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
        fun decode(input: ByteArray): StormVolume? { return try {
            if (input.size > MAX_BYTES) return null
            val data = if (input.size > 2 && input[0] == 0x1f.toByte() && input[1] == 0x8b.toByte()) {
                GZIPInputStream(input.inputStream()).use { readBounded(it) }
            } else input
            if (data.size < 12 || data.take(4) != "MCVX".toByteArray().toList()) return null
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.getInt(4) != 1) return null
            val length = buffer.getInt(8)
            if (length !in 2..131_072 || length > data.size - 12) return null
            val header = JsonParser.parseString(String(data, 12, length, Charsets.UTF_8)).asJsonObject
            val point = GeoPoint(header.number("lat") ?: return null, header.number("lon") ?: return null)
            fun dimension(key: String): Int? = header.number(key)?.takeIf { it in 1.0..256.0 && it == floor(it) }?.toInt()
            val nx = dimension("nx") ?: return null
            val ny = dimension("ny") ?: return null
            val nz = dimension("nz") ?: return null
            val size = nx.toLong() * ny * nz * 2
            if (!point.valid || size != (data.size - 12 - length).toLong()) return null
            val step = header.numbers("step_m")?.takeIf { it.size == 3 && it.all { n -> n > 0 && n <= 20_000 } } ?: return null
            val origin = header.numbers("origin_m")?.takeIf { it.size == 3 && it.all { n -> abs(n) <= 1_000_000 } } ?: return null
            val floor = header.number("dbz_floor")?.takeIf { it in -100.0..100.0 } ?: return null
            val scale = header.number("dbz_scale")?.takeIf { it > 0.01 && it <= 100 } ?: return null
            val rawApron = header.numbers("apron") ?: if (header.has("apron")) return null else listOf(0.0, 0.0, 0.0)
            if (rawApron.size != 3 || rawApron.any { it < 0 || it != kotlin.math.floor(it) }) return null
            val apron = rawApron.map { it.toInt() }
            if (apron.zip(listOf(nx, ny, nz)).any { (a, n) -> a.toLong() * 2 >= n }) return null
            StormVolume(point, nx, ny, nz, step, origin, floor, scale, apron, data.copyOfRange(12 + length, data.size), StormData.timestamp(header.word("scanned_at")) ?: StormData.timestamp(header.word("reference_time")), header.number("tier")?.toInt() ?: 2)
        } catch (_: Exception) { null } }
    }
}

/** WGS84 ECEF to east/north/up includes the curvature at distant storms. */
class GeoFrame(point: GeoPoint, altitude: Double) {
    data class Vector(val x: Double, val y: Double, val z: Double) {
        operator fun minus(b: Vector) = Vector(x - b.x, y - b.y, z - b.z)
        operator fun plus(b: Vector) = Vector(x + b.x, y + b.y, z + b.z)
        operator fun times(n: Double) = Vector(x * n, y * n, z * n)
        fun dot(b: Vector) = x * b.x + y * b.y + z * b.z
    }
    private val p = Math.toRadians(point.latitude)
    private val l = Math.toRadians(point.longitude)
    private val origin = ecef(point, altitude)
    val east = Vector(-sin(l), cos(l), 0.0)
    val north = Vector(-sin(p) * cos(l), -sin(p) * sin(l), cos(p))
    val up = Vector(cos(p) * cos(l), cos(p) * sin(l), sin(p))
    fun rotate(v: Vector) = Vector(v.dot(east), v.dot(north), v.dot(up))
    fun at(point: GeoPoint, altitude: Double) = rotate(ecef(point, altitude) - origin)
    val gaussianRadius: Double get() {
        val e2 = 0.0066943799901413165
        return 6_378_137.0 * sqrt(1 - e2) / (1 - e2 * sin(p).pow(2))
    }
    fun geodetic(enu: Vector): Pair<GeoPoint, Double>? {
        val point = origin + east * enu.x + north * enu.y + up * enu.z
        val a = 6_378_137.0
        val b = a * (1 - 1 / 298.257223563)
        val e2 = 0.0066943799901413165
        val ep2 = (a * a - b * b) / (b * b)
        val r = hypot(point.x, point.y)
        val angle = atan2(point.z * a, r * b)
        val latitude = atan2(point.z + ep2 * b * sin(angle).pow(3), r - e2 * a * cos(angle).pow(3))
        val n = a / sqrt(1 - e2 * sin(latitude).pow(2))
        val altitude = if (abs(cos(latitude)) > 1e-9) r / cos(latitude) - n else abs(point.z) - b
        val coordinate = GeoPoint(Math.toDegrees(latitude), Math.toDegrees(atan2(point.y, point.x)))
        return if (coordinate.valid && altitude.isFinite()) coordinate to altitude else null
    }
    companion object {
        private fun ecef(point: GeoPoint, altitude: Double): Vector {
            val p = Math.toRadians(point.latitude)
            val l = Math.toRadians(point.longitude)
            val e2 = 0.0066943799901413165
            val n = 6_378_137.0 / sqrt(1 - e2 * sin(p).pow(2))
            return Vector((n + altitude) * cos(p) * cos(l), (n + altitude) * cos(p) * sin(l), (n * (1 - e2) + altitude) * sin(p))
        }
    }
}
