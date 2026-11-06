package com.meteocool.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.location.Geocoder
import android.location.Address
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.location.service.LocationProviders
import com.meteocool.permissions.PermUtils
import com.meteocool.preferences.Prefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.Locale

class WidgetConfigActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var kind: WidgetKind
    private lateinit var form: LinearLayout
    private lateinit var placeList: LinearLayout
    private lateinit var saveButton: Button
    private var saveAfterPermission = false
    private val places = mutableListOf<WidgetPlace>()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (saveAfterPermission) { saveAfterPermission = false; saveWidget() } else refreshLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        kind = RainWidgetProvider.kind(provider?.className) ?: run { finish(); return }
        val config = WidgetConfig.decode(savedInstanceState?.getString("config")) ?: WidgetStore.load(this, widgetId)
            ?: WidgetConfig(range = if (kind == WidgetKind.PLACES) WidgetRange.HOUR else WidgetRange.TWO_HOURS)
        places.addAll(config.places)
        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 24) }
        val scroll = ScrollView(this).apply { addView(form) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        setContentView(scroll)
        label(getString(R.string.widget_configure), 24f)
        label(getString(R.string.widget_location_help), 14f)
        placeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(placeList)
        showPlaces()
        button(R.string.widget_current_location) {
            addPlace(WidgetPlace("current"))
            if (!PermUtils.isLocationPermissionGranted(this)) permission.launch(PermUtils.LOCATION_PERMISSIONS)
            else refreshLocation()
        }
        if (!PermUtils.isBackgroundLocationPermissionGranted(this)) {
            label(getString(R.string.widget_background_location_help), 14f)
            button(R.string.location_access) { startActivity(PermUtils.appSettingsIntent(this)) }
        }
        val query = EditText(this).apply {
            hint = getString(R.string.widget_search_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            setSingleLine(true)
        }
        form.addView(query)
        val search = button(R.string.widget_search) {}
        search.setOnClickListener {
            val text = query.text.toString().trim()
            if (text.isEmpty()) { query.error = getString(R.string.widget_search_hint); return@setOnClickListener }
            search.isEnabled = false
            lifecycleScope.launch {
                val results = searchPlaces(text)
                search.isEnabled = true
                if (results.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(this@WidgetConfigActivity)
                        .setMessage(if (results == null) R.string.widget_search_failed else R.string.widget_search_empty)
                        .setPositiveButton(android.R.string.ok, null).show()
                } else {
                    MaterialAlertDialogBuilder(this@WidgetConfigActivity).setTitle(R.string.widget_choose_place)
                        .setItems(results.map { it.name }.toTypedArray()) { _, index -> addPlace(results[index]) }.show()
                }
            }
        }
        val range = if (kind == WidgetKind.FORECAST || kind == WidgetKind.PLACES)
            choices(R.string.widget_range, R.array.widget_ranges, config.range.ordinal) else null
        val zoom = if (kind == WidgetKind.RADAR) choices(R.string.widget_area, R.array.widget_areas, WidgetConfig.zooms.indexOf(config.zoom)) else null
        val overlay = if (kind == WidgetKind.RADAR) choices(R.string.widget_overlay, R.array.widget_overlays, config.overlay) else null
        val span = if (kind == WidgetKind.CLOCK) choices(R.string.widget_dial, R.array.widget_dials, config.dialHours - 1) else null
        val intensity = choices(R.string.widget_intensity, R.array.widget_intensities, Prefs.INTENSITIES.indexOf(config.threshold) + 1)
        val map = CheckBox(this).apply { setText(R.string.widget_show_map); isChecked = config.showMap }
        if (kind == WidgetKind.FORECAST) form.addView(map)
        collectConfig = {
            WidgetConfig(places.toList(), range?.selectedItemPosition?.let { WidgetRange.entries[it] } ?: config.range,
                zoom?.selectedItemPosition?.let { WidgetConfig.zooms[it] } ?: config.zoom,
                overlay?.selectedItemPosition ?: config.overlay, Prefs.INTENSITIES.getOrNull(intensity.selectedItemPosition - 1),
                map.isChecked, (span?.selectedItemPosition ?: (config.dialHours - 1)) + 1)
        }
        saveButton = button(R.string.widget_save) {
            if (places.isEmpty()) addPlace(WidgetPlace("current"))
            if (places.any { it.current } && !PermUtils.isLocationPermissionGranted(this)) {
                saveAfterPermission = true
                permission.launch(PermUtils.LOCATION_PERMISSIONS)
            } else saveWidget()
        }
    }

    private var collectConfig: () -> WidgetConfig = { WidgetConfig() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("config", WidgetConfig.encode(collectConfig()))
        super.onSaveInstanceState(outState)
    }

    private fun refreshLocation() {
        lifecycleScope.launch {
            LocationProviders.currentLocation(this@WidgetConfigActivity)?.let { app.prefs.saveLastLocation(it) }
        }
    }

    private fun saveWidget() {
        saveButton.isEnabled = false
        lifecycleScope.launch {
            if (places.any { it.current } && PermUtils.isLocationPermissionGranted(this@WidgetConfigActivity) &&
                app.prefs.lastLocation(300_000) == null) {
                withTimeoutOrNull(15_000) { LocationProviders.currentLocation(this@WidgetConfigActivity) }
                    ?.let { app.prefs.saveLastLocation(it) }
            }
            WidgetStore.save(this@WidgetConfigActivity, widgetId, collectConfig())
            RainWidgetProvider.schedule(this@WidgetConfigActivity)
            RainWidgetProvider.refresh(this@WidgetConfigActivity, widgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }

    private fun addPlace(place: WidgetPlace) {
        if (kind != WidgetKind.PLACES) places.clear()
        if (place !in places && places.size < 6) places.add(place)
        showPlaces()
    }

    private fun showPlaces() {
        placeList.removeAllViews()
        places.forEach { place ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = if (place.current) getString(R.string.widget_current_location) else place.name },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                setText(R.string.widget_remove)
                setOnClickListener { places.remove(place); showPlaces() }
            })
            placeList.addView(row)
        }
    }

    private fun label(text: String, size: Float = 16f) {
        form.addView(TextView(this).apply { this.text = text; textSize = size; setPadding(0, 16, 0, 8) })
    }
    private fun button(text: Int, clicked: () -> Unit): Button = Button(this).apply {
        setText(text); setOnClickListener { clicked() }; form.addView(this)
    }
    private fun choices(label: Int, values: Int, selected: Int): Spinner {
        label(getString(label))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter.createFromResource(this@WidgetConfigActivity, values, android.R.layout.simple_spinner_dropdown_item)
            setSelection(selected.coerceAtLeast(0))
            contentDescription = getString(label)
        }
        form.addView(spinner)
        return spinner
    }

    private suspend fun searchPlaces(query: String): List<WidgetPlace>? {
        if (!Geocoder.isPresent()) return null
        return try {
            val addresses = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<List<Address>?> { continuation ->
                    Geocoder(this@WidgetConfigActivity, Locale.getDefault()).getFromLocationName(query, 5,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(addresses)
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                }
            } ?: return null
            addresses.mapNotNull { address ->
                if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null
                val name = listOfNotNull(address.locality ?: address.featureName, address.adminArea, address.countryName)
                    .distinct().joinToString(", ").ifBlank { query }.take(200)
                WidgetPlace(name, address.latitude, address.longitude).takeIf { it.valid() }
            }.distinct()
        } catch (_: IllegalArgumentException) { null } catch (_: SecurityException) { null }
    }
}
