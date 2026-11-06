package com.meteocool.ui.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import kotlin.math.cos
import kotlin.math.sin

class TapRun {
    private val times = ArrayDeque<Long>()
    fun tap(now: Long): Boolean {
        while (times.isNotEmpty() && now - times.first() > 1000) times.removeFirst()
        times.addLast(now)
        if (times.size < 3) return false
        times.clear()
        return true
    }
}

object LogoConfetti {
    fun burst(container: ViewGroup, source: View) {
        val position = IntArray(2)
        val origin = IntArray(2)
        source.getLocationInWindow(position)
        container.getLocationInWindow(origin)
        val x = position[0] - origin[0] + source.width / 2f
        val y = position[1] - origin[1] + source.height / 2f
        val calm = !ValueAnimator.areAnimatorsEnabled()
        val colors = intArrayOf(0xff007aff.toInt(), 0xff60b3d4.toInt(), 0xff34d65b.toInt(),
            0xffffe200.toInt(), 0xffff9300.toInt(), 0xffe90000.toInt(), 0xff9300ef.toInt())
        var progress = 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val animator = ValueAnimator.ofFloat(0f, 1f)
        val decoration = object : View(source.context) {
            init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false }
            override fun onDraw(canvas: Canvas) {
                repeat(42) { index ->
                    val angle = (index % 14) * Math.PI / 26
                    val speed = (0.25 + (index % 7) * 0.06) * width
                    val distance = if (calm) 25.0 + index else progress * speed
                    paint.color = colors[index % colors.size]
                    paint.alpha = if (calm) 200 else ((1 - progress) * 255).toInt()
                    val px = x + cos(angle) * distance
                    val py = y + sin(angle) * distance + if (calm) 0.0 else progress * progress * height * 0.7
                    canvas.drawCircle(px.toFloat(), py.toFloat(), 3 * resources.displayMetrics.density, paint)
                }
            }
            override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
        }
        container.addView(decoration, ViewGroup.LayoutParams(-1, -1))
        source.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (calm) decoration.postDelayed({ container.removeView(decoration) }, 600)
        else animator.apply {
            duration = 2500
            addUpdateListener { progress = it.animatedValue as Float; decoration.invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { container.removeView(decoration) }
            })
            start()
        }
    }
}
