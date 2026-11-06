package com.meteocool.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.format.DateFormat
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.preferences.RadarPalette
import com.meteocool.weather.RainForecast
import java.util.Date
import kotlin.math.max
import kotlin.math.min

/** Absolute times keep a launcher-delayed update from presenting an old countdown as current. */
object WidgetGraphics {
    fun time(context: Context, seconds: Long): String = DateFormat.getTimeFormat(context).format(Date(seconds * 1000))
    fun timestamp(context: Context, seconds: Long): String =
        DateFormat.getDateFormat(context).format(Date(seconds * 1000)) + " " + time(context, seconds)

    fun summary(context: Context, forecast: RainForecast, now: Long): String = when (val phase = forecast.phase(now)) {
        is RainForecast.Phase.Raining -> phase.end?.let { context.getString(R.string.widget_rain_until, time(context, it)) }
            ?: context.getString(R.string.widget_rain_continues)
        is RainForecast.Phase.Approaching -> context.getString(R.string.widget_rain_at, time(context, phase.arrival))
        RainForecast.Phase.Dry -> context.getString(R.string.widget_dry_through, time(context, forecast.at(forecast.dbz.lastIndex)))
        RainForecast.Phase.Unavailable -> context.getString(R.string.widget_unavailable)
    }

    fun chart(context: Context, forecast: RainForecast, range: WidgetRange, compact: Boolean = false): Bitmap {
        val width = if (compact) 240 else 480
        val height = if (compact) 60 else 140
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val start = max(0, forecast.observed - range.past)
        val end = min(forecast.dbz.size, forecast.observed + range.ahead)
        val count = (end - start).coerceAtLeast(1)
        val base = height.toFloat() - if (compact) 2f else 28f
        val step = width.toFloat() / count
        for (index in start until end) {
            val dbz = forecast.dbz[index]
            val left = (index - start) * step
            if (dbz == null) {
                paint.color = Color.LTGRAY
                canvas.drawLine(left, base - 10, left + step - 1, base, paint)
                continue
            }
            paint.color = RadarPalette.colour(dbz, context.app.prefs.radarColorMapping)?.let { it or Color.BLACK } ?: 0xFF6D7582.toInt()
            paint.alpha = if (dbz >= forecast.threshold) 255 else 140
            val barHeight = if (dbz <= 0) 2f else (dbz.coerceIn(0.0, 60.0) / 60 * (base - 4)).toFloat().coerceAtLeast(3f)
            canvas.drawRect(left + 1, base - barHeight, left + step - 1, base, paint)
        }
        paint.alpha = 255
        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        val nowX = (forecast.observed - start) * step
        canvas.drawLine(nowX, 0f, nowX, base, paint)
        if (!compact) {
            paint.textSize = 20f
            canvas.drawText(time(context, forecast.at(start)), 0f, height - 3f, paint)
            val endText = time(context, forecast.at((end - 1).coerceAtLeast(start)))
            canvas.drawText(endText, width - paint.measureText(endText), height - 3f, paint)
        }
        return bitmap
    }

    fun clock(context: Context, forecast: RainForecast, hours: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(260, 260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val count = hours * 12
        val sweep = 360f / count
        val bounds = RectF(20f, 20f, 240f, 240f)
        for (offset in 0 until count) {
            val dbz = forecast.dbz.getOrNull(forecast.observed + offset)
            paint.color = dbz?.let { RadarPalette.colour(it, context.app.prefs.radarColorMapping) }?.let { it or Color.BLACK }
                ?: 0xFF6D7582.toInt()
            paint.strokeWidth = if (dbz != null && dbz >= forecast.threshold) 18f else 8f
            canvas.drawArc(bounds, -90 + offset * sweep + 1f, sweep - 2f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 28f
        val label = context.getString(R.string.widget_clock_hours, hours)
        canvas.drawText(label, 130 - paint.measureText(label) / 2, 126f, paint)
        paint.textSize = 22f
        val start = time(context, forecast.at(forecast.observed))
        canvas.drawText(start, 130 - paint.measureText(start) / 2, 155f, paint)
        return bitmap
    }
}
