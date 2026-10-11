package com.meteocool.preferences

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.withClip
import kotlin.math.roundToInt

/** The radar map's colours, from [ColormapTables]. */
object RadarPalette {
    /** The colour bars run from drizzle on the left to hail on the right, in dBZ. */
    const val BAR_FROM_DBZ = 5.0
    const val BAR_TO_DBZ = 65.0

    /** 0xRRGGBB of a reflectivity, or null below the palette's first entry. Unknown palettes are Classic. */
    fun colour(dbz: Double, palette: String): Int? {
        val (leftPad, rgb) = ColormapTables.tables[palette] ?: ColormapTables.tables.getValue("classic")
        // Half-up, as the map rounds (and as Swift's .toNearestOrAwayFromZero for these positive values).
        val index = ((dbz + 32.5) * 2).roundToInt()
        if (index < leftPad || rgb.isEmpty()) return null
        return rgb[minOf(index - leftPad, rgb.size - 1)]
    }
}

/**
 * A palette as a bar, drizzle to hail, for the colour map picker. Steps below
 * the palette's first entry stay transparent, as on the map; [outline] keeps
 * the pale low end visible on a light background.
 */
class ColorBarDrawable(
    private val palette: String,
    private val widthPx: Int,
    private val heightPx: Int,
    private val cornerPx: Float,
    outline: Int,
) : Drawable() {
    private val fill = Paint()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * (heightPx / 14f).coerceAtLeast(1f)
        color = outline
    }
    private val clip = Path()

    override fun getIntrinsicWidth() = widthPx
    override fun getIntrinsicHeight() = heightPx

    override fun draw(canvas: Canvas) {
        val box = RectF(bounds)
        val inset = stroke.strokeWidth / 2
        val outlineBox = RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset)
        clip.reset()
        clip.addRoundRect(outlineBox, cornerPx, cornerPx, Path.Direction.CW)
        canvas.withClip(clip) {
            val steps = bounds.width().coerceAtLeast(1)
            for (step in 0 until steps) {
                val fraction = (step + 0.5) / steps
                val dbz = RadarPalette.BAR_FROM_DBZ + fraction * (RadarPalette.BAR_TO_DBZ - RadarPalette.BAR_FROM_DBZ)
                val rgb = RadarPalette.colour(dbz, palette) ?: continue
                fill.color = 0xFF000000.toInt() or rgb
                drawRect(box.left + step, box.top, box.left + step + 1, box.bottom, fill)
            }
        }
        canvas.drawRoundRect(outlineBox, cornerPx, cornerPx, stroke)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
