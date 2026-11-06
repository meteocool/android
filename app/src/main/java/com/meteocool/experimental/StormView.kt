package com.meteocool.experimental

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.SensorManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import com.meteocool.R
import com.meteocool.preferences.RadarPalette
import kotlin.math.*

/** Native perspective rendering of measured radar voxels over the camera or in a draggable preview. */
internal class StormView(context: Context, private val selected: (StormData.Storm) -> Unit) : View(context) {
    data class Cloud(val storm: StormData.Storm, val points: List<StormVolume.Voxel>, val centre: GeoPoint, val peelFloor: Double)
    private data class Dot(val position: GeoFrame.Vector, val dbz: Double, val confidence: Double, val altitude: Double)
    private data class DrawCloud(val storm: StormData.Storm, val points: List<Dot>, val label: GeoFrame.Vector, val centre: GeoFrame.Vector, val peelFloor: Double)
    private data class Hit(val x: Float, val y: Float, val storm: StormData.Storm)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics); setShadowLayer(4f, 0f, 1f, Color.BLACK) }
    private val clouds = mutableListOf<DrawCloud>()
    private val hits = mutableListOf<Hit>()
    private var viewer = GeoPoint(0.0, 0.0)
    private var altitude = 0.0
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private var hasOrientation = false
    private var yaw = 0.0
    private var orbitElevation = 0.3
    private var orbitZoom = 1.0
    var cameraMode = false; set(value) { field = value; invalidate() }
    var horizontalFov = Math.toRadians(55.0); set(value) { field = value; invalidate() }
    var declination = 0.0
    var mode = StormCut.Mode.LIVE; set(value) { field = value; invalidate() }
    var amount = 0.5; set(value) { field = value; invalidate() }
    var trackHeading: Double? = null
    var palette = "classic"
    var marker: SkyMarker? = null
    var selectedCell: StormData.Cell? = null
    var selectedTrack: StormData.Track? = null
    var targetPoint: GeoPoint? = null
    var onMarked: ((SkyMarker?) -> Unit)? = null
    var markArmed = false
        set(value) {
            field = value
            importantForAccessibility = if (value) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
            contentDescription = context.getString(if (value) R.string.experimental_ar_mark_instructions else R.string.experimental_volume_hint)
        }
    val peelFloor get() = clouds.maxOfOrNull { it.peelFloor } ?: 20.0
    private fun orbitCentre() = clouds.firstOrNull()?.let { GeoFrame(viewer, altitude).at(it.storm.point, 6_000.0) }
        ?: marker?.let { GeoFrame(viewer, altitude).at(it.point, it.altitude ?: altitude) }
        ?: GeoFrame.Vector(0.0, 0.0, 0.0)
    private fun ray(x: Float, y: Float): SkyMarkerRules.Ray? {
        if (width == 0 || height == 0 || cameraMode && !hasOrientation) return null
        val focal = width / (2 * tan(horizontalFov / 2))
        val dx = (x - width / 2) / focal
        val dy = -(y - height / 2) / focal
        val angle = Math.toRadians(yaw + if (cameraMode) declination else 0.0)
        val c = cos(angle); val s = sin(angle)
        val origin: GeoFrame.Vector
        val direction: GeoFrame.Vector
        if (cameraMode) {
            val east = remapped[0] * dx + remapped[1] * dy - remapped[2]
            val north = remapped[3] * dx + remapped[4] * dy - remapped[5]
            val up = remapped[6] * dx + remapped[7] * dy - remapped[8]
            direction = GeoFrame.Vector(east * c + north * s, -east * s + north * c, up)
            origin = GeoFrame.Vector(0.0, 0.0, 0.0)
        } else {
            val right = GeoFrame.Vector(c, -s, 0.0)
            val up = GeoFrame.Vector(-s * sin(orbitElevation), -c * sin(orbitElevation), cos(orbitElevation))
            val towardCamera = GeoFrame.Vector(s * cos(orbitElevation), c * cos(orbitElevation), sin(orbitElevation))
            origin = orbitCentre() + towardCamera * (60_000 / orbitZoom)
            direction = right * dx + up * dy - towardCamera
        }
        val length = sqrt(direction.dot(direction))
        return SkyMarkerRules.Ray(origin, direction * (1 / length))
    }
    private fun visible(dot: Dot, cloud: DrawCloud, bearing: Double, floor: Double): Boolean {
        val offset = dot.position - cloud.centre
        return StormCut.visible(mode, amount, offset.x, offset.y, dot.altitude, dot.dbz,
            bearing, trackHeading, altitude, floor)
    }
    private fun mark(x: Float, y: Float) {
        markArmed = false
        val ray = ray(x, y) ?: run { onMarked?.invoke(null); return }
        val floor = peelFloor
        val echoes = clouds.asSequence().flatMap { cloud ->
            val bearing = GeoMath.bearing(viewer, cloud.storm.point)
            cloud.points.asSequence().filter { it.dbz >= 24.9 && it.confidence > 0.3 && visible(it, cloud, bearing, floor) }
                .map { SkyMarkerRules.Echo(it.position, 600.0) }
        }
        onMarked?.invoke(SkyMarkerRules.mark(ray, GeoFrame(viewer, altitude), altitude, echoes))
    }
    var mesocyclones: List<StormData.Mesocyclone> = emptyList()
    var lightning: List<Strike> = emptyList()
    var showMesocyclones = true
    var showLightning = true
    var sunTap: ((Double, Double) -> Unit)? = null
    fun adjustForSun(degrees: Double) { yaw = GeoMath.normalize(yaw + degrees); invalidate() }
    private fun tappedDirection(x: Float, y: Float): Pair<Double, Double>? {
        val direction = ray(x, y)?.direction ?: return null
        return GeoMath.normalize(Math.toDegrees(atan2(direction.x, direction.y))) to Math.toDegrees(atan2(direction.z, hypot(direction.x, direction.y)))
    }
    var headingAdjusted: (() -> Unit)? = null
    init {
        isClickable = true
        contentDescription = context.getString(R.string.experimental_volume_hint)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun resetAlignment() { yaw = 0.0; invalidate() }
    fun orientation(matrix: FloatArray) { matrix.copyInto(rotation); hasOrientation = true; postInvalidateOnAnimation() }
    fun scene(values: List<Cloud>, point: GeoPoint, eyeAltitude: Double) {
        viewer = point; altitude = eyeAltitude
        clouds.clear()
        val frame = GeoFrame(viewer, altitude)
        for (cloud in values) {
            val origin = frame.at(cloud.centre, 0.0)
            val local = GeoFrame(cloud.centre, 0.0)
            val east = frame.rotate(local.east); val north = frame.rotate(local.north); val up = frame.rotate(local.up)
            val points = cloud.points.map { Dot(origin + east * it.east + north * it.north + up * it.altitude, it.dbz, it.confidence, it.altitude) }
            val top = cloud.points.maxOfOrNull { it.altitude } ?: 4_000.0
            clouds.add(DrawCloud(cloud.storm, points, frame.at(cloud.storm.point, top), frame.at(cloud.storm.point, 0.0), cloud.peelFloor))
        }
        invalidate()
    }
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            sunTap?.let { callback ->
                sunTap = null
                tappedDirection(e.x, e.y)?.let { callback(it.first, it.second) }
                performClick()
                return true
            }
            if (markArmed) { mark(e.x, e.y); performClick(); return true }
            val hit = hits.minByOrNull { hypot((it.x - e.x).toDouble(), (it.y - e.y).toDouble()) }
            if (hit != null && hypot((hit.x - e.x).toDouble(), (hit.y - e.y).toDouble()) <= 80 * resources.displayMetrics.density) selected(hit.storm)
            performClick(); return true
        }
        override fun onLongPress(e: MotionEvent) { if (sunTap == null) mark(e.x, e.y) }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            yaw = GeoMath.normalize(yaw + distanceX / width.coerceAtLeast(1) * if (cameraMode) Math.toDegrees(horizontalFov) else 180.0)
            if (!cameraMode) orbitElevation = (orbitElevation + distanceY / height.coerceAtLeast(1) * 2).coerceIn(-0.5, 1.4)
            headingAdjusted?.invoke(); invalidate(); return true
        }
    })
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!cameraMode) orbitZoom = (orbitZoom * detector.scaleFactor).coerceIn(0.2, 6.0)
            invalidate(); return true
        }
    })
    override fun onTouchEvent(event: MotionEvent): Boolean { scale.onTouchEvent(event); if (!scale.isInProgress) gestures.onTouchEvent(event); return true }
    override fun performClick(): Boolean { if (markArmed) mark(width / 2f, height / 2f); super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hits.clear()
        if (!cameraMode) canvas.drawColor(Color.rgb(12, 22, 35))
        if (cameraMode && !hasOrientation) return
        val xAxis: Int
        val yAxis: Int
        when (display?.rotation) {
            Surface.ROTATION_90 -> { xAxis = SensorManager.AXIS_Y; yAxis = SensorManager.AXIS_MINUS_X }
            Surface.ROTATION_180 -> { xAxis = SensorManager.AXIS_MINUS_X; yAxis = SensorManager.AXIS_MINUS_Y }
            Surface.ROTATION_270 -> { xAxis = SensorManager.AXIS_MINUS_Y; yAxis = SensorManager.AXIS_X }
            else -> { xAxis = SensorManager.AXIS_X; yAxis = SensorManager.AXIS_Y }
        }
        if (cameraMode) SensorManager.remapCoordinateSystem(rotation, xAxis, yAxis, remapped)
        val focal = width / (2 * tan(horizontalFov / 2))
        val centre = orbitCentre()
        val angle = Math.toRadians(yaw + if (cameraMode) declination else 0.0)
        val c = cos(angle); val s = sin(angle)
        fun project(p: GeoFrame.Vector): Triple<Float, Float, Double>? {
            val x: Double; val y: Double; val depth: Double
            if (cameraMode) {
                val east = p.x * c - p.y * s
                val north = p.x * s + p.y * c
                x = remapped[0] * east + remapped[3] * north + remapped[6] * p.z
                y = remapped[1] * east + remapped[4] * north + remapped[7] * p.z
                depth = -(remapped[2] * east + remapped[5] * north + remapped[8] * p.z)
            } else {
                val v = p - centre
                x = v.x * c - v.y * s
                val away = v.x * s + v.y * c
                y = v.z * cos(orbitElevation) - away * sin(orbitElevation)
                depth = 60_000 / orbitZoom - (away * cos(orbitElevation) + v.z * sin(orbitElevation))
            }
            if (depth < 100 || !depth.isFinite()) return null
            val px = width / 2 + focal * x / depth
            val py = height / 2 - focal * y / depth
            if (px < -100 || px > width + 100 || py < -100 || py > height + 100) return null
            return Triple(px.toFloat(), py.toFloat(), depth)
        }
        val floor = peelFloor
        for (cloud in clouds.sortedByDescending { it.label.dot(it.label) }) {
            val bearing = GeoMath.bearing(viewer, cloud.storm.point)
            for (dot in cloud.points) {
                if (!visible(dot, cloud, bearing, floor)) continue
                val p = project(dot.position) ?: continue
                fill.color = (0xA0000000.toInt() or (RadarPalette.colour(dot.dbz, palette) ?: continue))
                fill.alpha = (60 + 100 * dot.confidence).toInt()
                val radius = (focal * 600 / p.third).coerceIn(1.3, 24.0).toFloat()
                canvas.drawCircle(p.first, p.second, radius, fill)
            }
            val label = project(cloud.label) ?: continue
            val km = GeoMath.distance(viewer, cloud.storm.point) / 1_000
            val peak = cloud.storm.peakDbz ?: cloud.points.maxOfOrNull { it.dbz }
            val text = String.format(java.util.Locale.getDefault(), "%.1f km", km) + (peak?.let { String.format(java.util.Locale.getDefault(), " · %.0f dBZ", it) } ?: "")
            canvas.drawText(text, (label.first - textPaint.measureText(text) / 2).coerceIn(0f, (width - textPaint.measureText(text)).coerceAtLeast(0f)), label.second, textPaint)
            hits.add(Hit(label.first, label.second, cloud.storm))
        }
        val overlayFrame = GeoFrame(viewer, altitude)
        val now = System.currentTimeMillis() / 1_000.0
        fun line(points: List<GeoPoint>, color: Int, widthDp: Float = 2f) {
            fill.color = color; fill.alpha = 220; fill.strokeWidth = widthDp * resources.displayMetrics.density
            var previous: Triple<Float, Float, Double>? = null
            for (point in points) {
                val next = project(overlayFrame.at(point, altitude))
                if (next != null && previous != null) canvas.drawLine(previous.first, previous.second, next.first, next.second, fill)
                previous = next
            }
        }
        selectedTrack?.let { line(it.points, Color.rgb(255, 155, 50), 3f) }
        selectedCell?.let { cell ->
            val cyan = Color.rgb(100, 230, 255)
            StormTracks.motionArrow(cell.point, cell.heading, cell.speedKmh).forEach { line(it, cyan, 3f) }
            for (forecast in cell.forecast) {
                line(StormTracks.ellipse(forecast.point, forecast.majorKm * 1_000, forecast.minorKm * 1_000, forecast.angle), cyan, 1.5f)
            }
            line(cell.forecast.map { it.point }, cyan, 1.5f)
        }
        targetPoint?.let { point ->
            val radius = max(4_000.0, sqrt((selectedCell?.areaKm2 ?: 50.0).coerceAtLeast(0.0) / PI) * 1_000)
            line(StormTracks.ellipse(point, radius, radius, 0.0), Color.WHITE, 1f)
            val bearing = GeoMath.bearing(viewer, point)
            val side = GeoMath.destination(point, radius + 1_500, bearing + 90)
            var previous: Triple<Float, Float, Double>? = null
            for (km in 0..16 step 2) {
                val tick = project(overlayFrame.at(side, km * 1_000.0))
                fill.color = Color.WHITE; fill.alpha = 180; fill.strokeWidth = resources.displayMetrics.density
                if (tick != null && previous != null) canvas.drawLine(previous.first, previous.second, tick.first, tick.second, fill)
                if (tick != null && km > 0) {
                    canvas.drawLine(tick.first, tick.second, tick.first + 8 * resources.displayMetrics.density, tick.second, fill)
                    canvas.drawText(km.toString() + " km", tick.first + 12 * resources.displayMetrics.density, tick.second, textPaint)
                }
                previous = tick
            }
        }
        if (cameraMode) {
            val heading = tappedDirection(width / 2f, height / 2f)?.first
            if (heading != null) {
                val compass = context.getString(R.string.experimental_ar_compass_points).split(" ").filter { it.isNotEmpty() }
                val direction = if (compass.size == 16) compass[(heading / 22.5).roundToInt() % 16] else GeoMath.compass8(heading)
                canvas.drawText(String.format(java.util.Locale.getDefault(), "%s %.0f°", direction, heading), 12 * resources.displayMetrics.density, 24 * resources.displayMetrics.density, textPaint)
                val target = targetPoint ?: marker?.point
                if (target != null) {
                    val off = GeoMath.difference(GeoMath.bearing(viewer, target), heading)
                    if (abs(off) > Math.toDegrees(horizontalFov) / 2 - 5) {
                        val guidance = context.getString(if (off < 0) R.string.experimental_ar_turn_left else R.string.experimental_ar_turn_right, abs(off))
                        canvas.drawText(guidance, 12 * resources.displayMetrics.density, 46 * resources.displayMetrics.density, textPaint)
                    }
                }
            }
        }
        if (showMesocyclones) for (meso in mesocyclones) {
            if (!StormData.fresh(meso.time, now) || GeoMath.distance(viewer, meso.point) > 180_000) continue
            fill.color = Color.rgb(255, 75, 230); fill.alpha = 230; fill.strokeWidth = 3 * resources.displayMetrics.density
            var previous: Triple<Float, Float, Double>? = null
            for (degree in 0..360 step 10) {
                val ring = GeoMath.destination(meso.point, max(1_000.0, meso.diameter / 2), degree.toDouble())
                val current = project(overlayFrame.at(ring, altitude))
                if (previous != null && current != null) canvas.drawLine(previous.first, previous.second, current.first, current.second, fill)
                previous = current
            }
            project(overlayFrame.at(meso.point, altitude + 1_000))?.let { tag ->
                canvas.drawText(context.getString(R.string.experimental_native_map_mesocyclone, meso.intensity), tag.first, tag.second, textPaint)
            }
        }
        if (showLightning) for (strike in lightning) {
            val age = now - strike.time
            if (age !in -60.0..600.0 || GeoMath.distance(viewer, strike.point) > 180_000) continue
            val base = project(overlayFrame.at(strike.point, altitude)) ?: continue
            val top = project(overlayFrame.at(strike.point, altitude + 1_500)) ?: continue
            fill.color = Color.rgb(255, 242, 140); fill.alpha = (255 * (1 - max(0.0, age) / 600)).toInt(); fill.strokeWidth = (if (age < 60) 3 else 1) * resources.displayMetrics.density
            canvas.drawLine(top.first, top.second, base.first + 3, (top.second + base.second) / 2, fill)
            canvas.drawLine(base.first + 3, (top.second + base.second) / 2, base.first, base.second, fill)
        }
        marker?.let { marker ->
            val point = marker.point
            val frame = GeoFrame(viewer, altitude)
            val metres = GeoMath.distance(viewer, point)
            val base = project(frame.at(point, altitude))
            val top = project(frame.at(point, max(altitude + 50.0, marker.altitude ?: (altitude + (metres * 0.12).coerceIn(120.0, 6_000.0)))))
            if (top != null) {
                fill.color = Color.rgb(255, 60, 50); fill.alpha = 255; fill.strokeWidth = 5 * resources.displayMetrics.density
                if (base != null) canvas.drawLine(base.first, base.second, top.first, top.second, fill)
                canvas.drawCircle(top.first, top.second, 7 * resources.displayMetrics.density, fill)
                canvas.drawText(context.getString(R.string.experimental_ar_marker), top.first + 10, top.second, textPaint)
            }
        }
        if (cameraMode) {
            fill.color = Color.WHITE; fill.alpha = 150; fill.strokeWidth = 2f
            canvas.drawLine(width / 2f - 12, height / 2f, width / 2f + 12, height / 2f, fill)
            canvas.drawLine(width / 2f, height / 2f - 12, width / 2f, height / 2f + 12, fill)
        }
    }
}
