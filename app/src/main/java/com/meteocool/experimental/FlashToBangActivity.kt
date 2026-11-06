package com.meteocool.experimental

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.meteocool.R
import com.meteocool.permissions.PermUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

class FlashToBangActivity : ExperimentalActivity() {
    private val session = FlashToBangRules.Session()
    private val strikes = LinkedHashSet<Strike>()
    private lateinit var feed: StormFeed
    private lateinit var target: MaterialButton
    private lateinit var report: TextView
    private lateinit var status: TextView
    private lateinit var map: MaterialButton
    private var feedOk = false
    private var locating = false
    private var manualLocation: Job? = null
    private var lastTick = 0.0
    private val wallAnchor = System.currentTimeMillis() / 1_000.0
    private val monoAnchor = SystemClock.elapsedRealtime()
    private fun now() = wallAnchor + (SystemClock.elapsedRealtime() - monoAnchor) / 1_000.0
    private fun decimal(n: Double) = NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(n)

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!enabled) return
        feed = StormFeed()
        title = getString(R.string.experimental_flash_bang)
        val content = column()
        content.addView(button(getString(R.string.experimental_ar_close)) { finish() })
        content.addView(label(title, 26f))
        content.addView(label(getString(R.string.experimental_safety)))
        status = label(getString(R.string.experimental_ar_finding_location)); content.addView(status)
        content.addView(button(getString(R.string.location_button)) { requestLocation() })
        content.addView(button(getString(R.string.experimental_ar_open_settings)) { appSettings() })
        report = label(size = 22f); content.addView(report)
        target = button(getString(R.string.experimental_flash_bang_flash)) {
            val point = place ?: return@button
            when (session.tap(now(), point)) {
                FlashToBangRules.Session.Tap.FLASH -> target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                FlashToBangRules.Session.Tap.BANG -> target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                FlashToBangRules.Session.Tap.IGNORED -> Unit
            }
            update()
        }.apply {
            minHeight = dp(100)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (view.isEnabled) { view.isPressed = true; view.performClick() }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.isPressed = false
                }
                true
            }
        }
        content.addView(target)
        content.addView(label(getString(R.string.experimental_flash_bang_flash_hint)))
        map = button(getString(R.string.experimental_flash_bang_show)) {
            session.measurement?.match?.strike?.point?.let { openMap("?layer=lightning&lat=${it.latitude}&lon=${it.longitude}&zoom=12") }
        }; content.addView(map)
        val scroll = ScrollView(this).apply { addView(content) }; fitInsets(scroll); setContentView(scroll)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { refreshLocation(); while (true) { delay(30_000); refreshLocation() } }
                launch {
                    while (true) {
                        place?.let { point ->
                            val found = feed.strikes(point, now())
                            feedOk = found != null
                            if (found != null) {
                                val additions = found.filter { it !in strikes }
                                strikes.addAll(found)
                                if (now() - lastTick > 0.2 && additions.any { now() - it.time < 10 && GeoMath.distance(point, it.point) <= FlashToBangRules.TICK_WITHIN }) {
                                    lastTick = now(); target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            update()
                        }
                        delay(3_000)
                    }
                }
                launch { while (true) { update(); delay(100) } }
            }
        }
        if (!PermUtils.isLocationPermissionGranted(this)) requestLocation()
    }
    override fun permissionsChanged() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        manualLocation?.cancel()
        manualLocation = lifecycleScope.launch { refreshLocation() }
    }
    private suspend fun refreshLocation() {
        if (locating) return
        locating = true
        try { locate() } finally { locating = false }
        update()
    }
    private fun update() {
        if (!::target.isInitialized) return
        val now = now()
        strikes.removeAll { now - it.time > FlashToBangRules.KEEP_FOR }
        val previous = session.measurement?.match
        session.update(now, strikes)
        if (previous == null && session.measurement?.match != null) target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        target.isEnabled = place != null
        status.text = when {
            place == null -> getString(R.string.experimental_ar_location_denied)
            !feedOk -> getString(R.string.experimental_ar_status_failed)
            else -> ""
        }
        val counting = session.phase == FlashToBangRules.Session.Phase.COUNTING
        target.text = getString(if (counting) R.string.experimental_flash_bang_thunder else R.string.experimental_flash_bang_flash) + if (counting) "\n${decimal((now - session.flash).coerceAtLeast(0.0))} s" else ""
        target.contentDescription = getString(if (counting) R.string.experimental_flash_bang_thunder_hint else R.string.experimental_flash_bang_flash_hint)
        map.visibility = if (session.measurement?.match == null) View.GONE else View.VISIBLE
        val measurement = session.measurement
        report.text = when {
            session.phase == FlashToBangRules.Session.Phase.TOO_FAR -> getString(R.string.experimental_no_thunder)
            measurement == null -> ""
            else -> buildString {
                val match = measurement.match
                if (match != null) {
                    append("${decimal(match.distance / 1_000)} km ${GeoMath.compass8(match.bearing)} (${match.bearing.toInt()}°)\n")
                    val quality = when (match.quality) {
                        FlashToBangRules.Quality.CLOSE -> R.string.experimental_flash_bang_close
                        FlashToBangRules.Quality.FAIR -> R.string.experimental_flash_bang_fair
                        FlashToBangRules.Quality.LOOSE -> R.string.experimental_flash_bang_loose
                    }
                    append(getString(R.string.experimental_flash_bang_matched, DateFormat.getTimeInstance().format(Date((match.strike.time * 1_000).toLong())), getString(quality)))
                    append('\n')
                } else {
                    append(getString(R.string.experimental_flash_bang_about, decimal(measurement.estimate / 1_000))); append('\n')
                    append(getString(if (measurement.searching) R.string.experimental_flash_bang_searching else R.string.experimental_flash_bang_no_match)); append('\n')
                }
                append(getString(R.string.experimental_flash_bang_counted_about, decimal(measurement.gap), decimal(measurement.estimate / 1_000)))
                if (measurement.withinStrikingRange) append("\n" + getString(R.string.experimental_flash_bang_shelter))
            }
        }
    }
    override fun onStop() {
        manualLocation?.cancel()
        if (session.phase == FlashToBangRules.Session.Phase.COUNTING) session.reset()
        session.measurement?.searching = false
        super.onStop()
    }
}
