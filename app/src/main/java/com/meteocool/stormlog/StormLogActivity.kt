package com.meteocool.stormlog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.network.ApiClient
import com.meteocool.notifications.Registration
import com.meteocool.ui.MeteocoolActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** An in-memory native log. Opening it never starts logging without explicit consent and a successful wipe. */
class StormLogActivity : AppCompatActivity() {
    private lateinit var content: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private val consent by lazy { StormLogConsent(app.prefs.sp) }
    private val api = ApiClient()
    private var job: Job? = null
    private var entries = emptyList<StormPassage>()
    private var detail: StormPassage? = null
    private var session: Session? = null

    private data class Session(val apiBase: String, val token: String, val mapHost: String) {
        val key: String get() = StormLogRules.context(apiBase, token)
    }

    private fun currentSession(): Session? {
        if (MeteocoolEnvironment.current != MeteocoolEnvironment.STAGING) return null
        val token = app.prefs.pushToken?.takeIf { Registration.isValidToken(it) } ?: return null
        return Session(MeteocoolEnvironment.currentApiBase, token, MeteocoolEnvironment.current.webHost)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (MeteocoolEnvironment.current != MeteocoolEnvironment.STAGING) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.storm_log_title)
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            navigationContentDescription = getString(R.string.storm_log_cancel)
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        root.addView(toolbar)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (detail != null) { detail = null; showList() } else finish()
            }
        })
        lifecycleScope.launch {
            MeteocoolEnvironment.changes.drop(1).collect { if (it != MeteocoolEnvironment.STAGING) finish() }
        }
        start()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && currentSession() != session) start()
    }

    private fun start() {
        job?.cancel()
        entries = emptyList()
        detail = null
        session = currentSession()
        val current = session
        when {
            current == null -> message(R.string.storm_log_unregistered) { start() }
            consent.pending(current.key) -> message(R.string.storm_log_wipe_pending) { wipe(current) }
            consent.enabled(current.key) -> load(current)
            else -> showConsent(current)
        }
    }

    private fun current(expected: Session) = !isFinishing && expected == currentSession() && expected == session

    private fun column(scrolling: Boolean = true): LinearLayout {
        content.removeAllViews()
        toolbar.title = getString(R.string.storm_log_title)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(20))
        }
        if (scrolling) content.addView(ScrollView(this).apply { addView(column) }) else content.addView(column)
        return column
    }

    private fun LinearLayout.words(value: String, large: Boolean = false) {
        addView(TextView(this@StormLogActivity).apply {
            text = value
            textSize = if (large) 20f else 16f
            setPadding(0, dp(8), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun LinearLayout.action(label: Int, onClick: () -> Unit) {
        addView(MaterialButton(this@StormLogActivity).apply {
            setText(label)
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun message(text: Int, retry: (() -> Unit)? = null) {
        val column = column()
        column.words(getString(text))
        if (retry != null) column.action(R.string.storm_log_retry, retry)
        session?.takeIf { consent.enabled(it.key) || consent.enabling(it.key) }?.let { current ->
            column.action(R.string.storm_log_turn_off) { confirm(current, disable = true) }
        }
    }

    private fun loading() { column().addView(ProgressBar(this)) }

    private fun showConsent(current: Session) {
        val column = column()
        for (text in listOf(R.string.storm_log_onboarding_message, R.string.storm_log_onboarding_saves_text,
            R.string.storm_log_onboarding_area_text, R.string.storm_log_onboarding_server_text,
            R.string.storm_log_onboarding_off_text, R.string.storm_log_onboarding_footnote)) {
            column.words(getString(text))
        }
        column.action(R.string.storm_log_turn_on) {
            if (!current(current)) return@action
            consent.begin(current.key, enableAfterWipe = true)
            wipe(current)
        }
    }

    private fun load(current: Session) {
        job?.cancel()
        loading()
        job = lifecycleScope.launch {
            val response = api.request(current.apiBase, "${StormLogRules.PATH}?limit=${StormLogRules.LIMIT}",
                headers = mapOf(StormLogRules.TOKEN_HEADER to current.token))
            if (!current(current) || !consent.enabled(current.key)) return@launch
            val loaded = response?.takeIf { it.status in 200..299 }?.let { StormLogRules.decode(it.body) }
            if (loaded == null) { entries = emptyList(); message(R.string.storm_log_failed) { load(current) } }
            else { entries = loaded; showList() }
        }
    }

    private fun wipe(current: Session) {
        if (!current(current) || !consent.pending(current.key)) return
        job?.cancel()
        entries = emptyList()
        detail = null
        loading()
        job = lifecycleScope.launch {
            // Stop collection before deleting; the registration mutex drains any earlier enabled POST.
            if (app.registration.canRegister()) app.registration.refreshRegistration().join()
            else app.registration.refreshAuthorization().join()
            if (!current(current)) return@launch
            if (app.registration.syncFailed.value || (!app.registration.canRegister() && app.prefs.registrationOrigin != null)) {
                message(R.string.storm_log_wipe_pending) { wipe(current) }
                return@launch
            }
            val response = api.request(current.apiBase, StormLogRules.PATH, "DELETE",
                mapOf(StormLogRules.TOKEN_HEADER to current.token))
            if (!current(current)) return@launch
            if (response != null && StormLogRules.deleted(response.status, response.body)) {
                consent.complete(current.key)
                if (consent.enabled(current.key)) showList() else showConsent(current)
            } else message(R.string.storm_log_wipe_pending) { wipe(current) }
        }
    }

    private fun confirm(current: Session, disable: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle(if (disable) R.string.storm_log_turn_off_title else R.string.storm_log_delete_title)
            .setMessage(if (disable) R.string.storm_log_turn_off_message else R.string.storm_log_delete_message)
            .setNegativeButton(R.string.storm_log_cancel, null)
            .setPositiveButton(if (disable) R.string.storm_log_turn_off_confirm else R.string.storm_log_delete) { _, _ ->
                if (current(current)) {
                    consent.begin(current.key, enableAfterWipe = !disable)
                    wipe(current)
                }
            }.show()
    }

    private fun sort(): StormSort = StormSort.entries.firstOrNull {
        it.name.lowercase(Locale.ROOT) == app.prefs.sp.getString(StormLogConsent.SORT, "recent")
    } ?: StormSort.RECENT

    private fun showList() {
        val current = session?.takeIf { current(it) && consent.enabled(it.key) } ?: return start()
        val column = column(scrolling = entries.isEmpty())
        if (entries.isEmpty()) column.words(getString(R.string.storm_log_empty))
        val footer = if (entries.isEmpty()) column else LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        footer.words(getString(R.string.storm_log_footer))
        footer.action(R.string.storm_log_refresh) { load(current) }
        footer.action(R.string.storm_log_delete) { confirm(current, disable = false) }
        footer.action(R.string.storm_log_turn_off) { confirm(current, disable = true) }
        if (entries.isNotEmpty()) {
            val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            header.action(sortTitle(sort())) {
                val orders = StormSort.entries
                MaterialAlertDialogBuilder(this).setTitle(R.string.storm_log_sort)
                    .setSingleChoiceItems(orders.map { getString(sortTitle(it)) }.toTypedArray(), sort().ordinal) { dialog, which ->
                        app.prefs.sp.edit { putString(StormLogConsent.SORT, orders[which].name.lowercase(Locale.ROOT)) }
                        dialog.dismiss()
                        showList()
                    }.show()
            }
            val sorted = StormLogRules.sorted(entries, sort())
            val list = ListView(this).apply {
                addHeaderView(header, null, false)
                addFooterView(footer, null, false)
                adapter = object : BaseAdapter() {
                    override fun getCount() = sorted.size
                    override fun getItem(position: Int) = sorted[position]
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
                        ((convertView as? TextView) ?: TextView(this@StormLogActivity).apply {
                            textSize = 16f
                            setPadding(dp(4), dp(16), dp(4), dp(16))
                            minHeight = dp(72)
                        }).apply {
                            val p = sorted[position]
                            text = "${getString(severity(p.severity))} · ${place(p)}\n${whenText(p)} · ${distance(p)}" +
                                if (p.endedAt == null) "\n${getString(R.string.storm_log_ongoing)}" else ""
                        }
                }
                setOnItemClickListener { _, _, position, _ -> sorted.getOrNull(position - headerViewsCount)?.let(::showDetail) }
            }
            column.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun showDetail(p: StormPassage) {
        val current = session?.takeIf { current(it) && consent.enabled(it.key) } ?: return start()
        detail = p
        val column = column()
        toolbar.title = getString(severity(p.severity))
        column.words(getString(R.string.storm_log_track), large = true)
        column.addView(StormTrackView(this, p), LinearLayout.LayoutParams(-1, dp(240)))
        column.words(getString(R.string.storm_log_area_note))
        fun reading(label: Int, value: String) = column.words("${getString(label)}: $value")
        reading(R.string.storm_log_place, place(p))
        reading(R.string.storm_log_when, whenText(p))
        reading(R.string.storm_log_closest_label, distance(p))
        reading(R.string.storm_log_severity, getString(severity(p.severity)))
        p.peakDbz?.let { reading(R.string.storm_log_peak, "${number(it, 0)} dBZ") }
        p.topKm?.let { reading(R.string.storm_log_top_label, "${number(it, 1)} km") }
        p.vil?.let { column.words("VIL: ${number(it, 0)} kg/m²") }
        reading(R.string.storm_log_strikes_label, NumberFormat.getIntegerInstance().format(p.strikes))
        if (p.hail) reading(R.string.storm_log_hail, getString(R.string.storm_log_yes))
        if (p.meso) reading(R.string.storm_log_meso, getString(R.string.storm_log_yes))
        if (p.endedAt == null) column.words(getString(R.string.storm_log_ongoing_footer))
        StormLogRules.shareUrl(p.shareUrl, current.mapHost)?.let { url ->
            column.action(R.string.show_radar) {
                if (current(current)) startActivity(Intent(this, MeteocoolActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(url)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
            column.action(R.string.map_share) {
                if (current(current)) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }, getString(R.string.map_share)))
            }
        }
    }

    private fun place(p: StormPassage) = p.placeName ?: getString(R.string.storm_log_my_location)
    private fun distance(p: StormPassage) = if (p.over) getString(R.string.storm_log_over)
        else getString(R.string.storm_log_closest, "${number(p.closestKm, if (p.closestKm < 10) 1 else 0)} km")
    private fun whenText(p: StormPassage): String {
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return format.format(Date(p.startedAt)) + (p.endedAt?.let { " – ${format.format(Date(it))}" } ?: "")
    }
    private fun number(value: Double, digits: Int) = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = digits; maximumFractionDigits = digits
    }.format(value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun severity(value: Int) = listOf(R.string.storm_log_weak, R.string.storm_log_moderate,
        R.string.storm_log_strong, R.string.storm_log_extreme)[value.coerceIn(0, 3)]
    private fun sortTitle(value: StormSort) = when (value) {
        StormSort.RECENT -> R.string.storm_log_sort_recent
        StormSort.SEVERITY -> R.string.storm_log_sort_severity
        StormSort.CLOSEST -> R.string.storm_log_sort_closest
        StormSort.DURATION -> R.string.storm_log_sort_duration
        StormSort.LIGHTNING -> R.string.storm_log_sort_lightning
    }
}
