package com.meteocool.experimental

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.MeteocoolLocation
import com.meteocool.location.service.LocationProviders
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.MeteocoolActivity
import kotlinx.coroutines.launch

abstract class ExperimentalActivity : AppCompatActivity() {
    protected val enabled get() = MeteocoolEnvironment.current == MeteocoolEnvironment.STAGING
    protected var fix: MeteocoolLocation? = null
    protected val place: GeoPoint? get() = fix?.takeIf {
        (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1e9 in 0.0..120.0 && it.accuracy.isFinite() && it.accuracy >= 0
    }?.let { GeoPoint(it.latitude, it.longitude, it.accuracy.toDouble()) }?.takeIf { it.valid }
    protected val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsChanged() }
    protected open fun permissionsChanged() {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!enabled) { finish(); return }
        lifecycleScope.launch { MeteocoolEnvironment.changes.collect { if (!enabled) finish() } }
    }
    protected suspend fun locate() { fix = LocationProviders.currentLocation(this) }
    protected fun requestLocation() {
        if (PermUtils.isLocationPermissionGranted(this)) permissionsChanged()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    protected fun appSettings() { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    protected fun openMap(search: String) {
        if (!enabled) { finish(); return }
        startActivity(Intent(this, MeteocoolActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(MeteocoolEnvironment.current.webHost + "/" + search)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
    protected fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
    }
    protected fun label(text: CharSequence = "", size: Float = 16f) = TextView(this).apply {
        this.text = text; textSize = size; setPadding(0, dp(6), 0, dp(6))
    }
    protected fun button(text: CharSequence, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text; setOnClickListener { action() }
        minHeight = dp(48)
    }
    protected fun fitInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
