package com.meteocool.stormlog

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.meteocool.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A north-up track diagram with a coarse area, never an exact location pin. */
class StormTrackView(context: Context, private val passage: StormPassage) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    init {
        contentDescription = context.getString(R.string.storm_log_area_note)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val east = 111_195 * max(cos(Math.toRadians(passage.latitude)), 0.01)
        val radius = StormLogRules.approximateRadius(passage)
        val points = passage.track.map { point ->
            // Keep a track crossing the date line in the same local frame.
            val longitude = ((point.longitude - passage.longitude + 540) % 360) - 180
            longitude * east to (point.latitude - passage.latitude) * 111_195
        }
        val west = min(-radius, points.minOfOrNull { it.first } ?: 0.0)
        val right = max(radius, points.maxOfOrNull { it.first } ?: 0.0)
        val south = min(-radius, points.minOfOrNull { it.second } ?: 0.0)
        val north = max(radius, points.maxOfOrNull { it.second } ?: 0.0)
        val inset = 24 * density
        val scale = min((width - inset * 2) / (right - west), (height - inset * 2) / (north - south))
        fun x(value: Double) = (width / 2 + (value - (west + right) / 2) * scale).toFloat()
        fun y(value: Double) = (height / 2 - (value - (south + north) / 2) * scale).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 35, 135, 230)
        canvas.drawCircle(x(0.0), y(0.0), (radius * scale).toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.color = Color.rgb(35, 135, 230)
        canvas.drawCircle(x(0.0), y(0.0), (radius * scale).toFloat(), paint)
        paint.color = intArrayOf(0xff2f9e44.toInt(), 0xfff0b429.toInt(), 0xffe03131.toInt(), 0xff9c36b5.toInt())[passage.severity]
        paint.strokeWidth = 4 * density
        paint.strokeCap = Paint.Cap.ROUND
        val path = Path()
        points.forEachIndexed { index, point -> if (index == 0) path.moveTo(x(point.first), y(point.second)) else path.lineTo(x(point.first), y(point.second)) }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        points.lastOrNull()?.let { canvas.drawCircle(x(it.first), y(it.second), 6 * density, paint) }
    }
}
