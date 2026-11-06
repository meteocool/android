package com.meteocool.weather

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.R
import com.meteocool.location.service.LocationProviders
import com.meteocool.permissions.PermUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RainStatusActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private var loading: Job? = null
    private val location = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val notification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) track() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(column) })
        ViewCompat.setOnApplyWindowInsetsListener(column) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val pad = (24 * resources.displayMetrics.density).toInt()
            view.updatePadding(left = bars.left + pad, top = bars.top + pad, right = bars.right + pad, bottom = bars.bottom + pad)
            insets
        }
        fun button(label: Int, action: () -> Unit) { column.addView(MaterialButton(this).apply { setText(label); setOnClickListener { action() } }) }
        button(R.string.dismiss) { finish() }
        status = TextView(this).apply { setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline5) }
        column.addView(status)
        column.addView(TextView(this).apply { setText(R.string.rain_monitor_help) })
        button(R.string.rain_refresh) {
            if (PermUtils.isLocationPermissionGranted(this)) refresh() else location.launch(PermUtils.LOCATION_PERMISSIONS)
        }
        button(R.string.rain_track) { track() }
        button(R.string.rain_stop) { stopService(Intent(this, RainMonitorService::class.java)); refresh() }
        refresh()
    }

    private fun refresh() {
        loading?.cancel()
        loading = lifecycleScope.launch {
            status.setText(R.string.rain_checking)
            val fix = LocationProviders.currentLocation(this@RainStatusActivity)
            val forecast = fix?.takeIf { System.currentTimeMillis() - it.timeMillis in 0..60_000 && it.accuracy in 0f..1000f }
                ?.let { WeatherData.forecast(this@RainStatusActivity, it.latitude, it.longitude) }
            status.text = forecast?.let { RainText.summary(this@RainStatusActivity, it) } ?: getString(R.string.rain_unavailable)
        }
    }

    private fun track() {
        if (!PermUtils.isLocationPermissionGranted(this)) { location.launch(PermUtils.LOCATION_PERMISSIONS); return }
        if (PermUtils.needsNotificationPermission(this)) {
            notification.launch(Manifest.permission.POST_NOTIFICATIONS); return
        }
        if (!PermUtils.areNotificationsEnabled(this)) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.notification_permissions_help)
                .setPositiveButton(R.string.change_in_settings) { _, _ -> startActivity(PermUtils.appSettingsIntent(this)) }
                .setNegativeButton(R.string.dismiss, null).show()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, RainMonitorService::class.java))
        refresh()
    }
}
