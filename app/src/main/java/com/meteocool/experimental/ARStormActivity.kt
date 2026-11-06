package com.meteocool.experimental

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.TextureView
import com.google.android.material.button.MaterialButton
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import java.text.DateFormat
import java.util.Date
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.R
import com.meteocool.app.MeteocoolApp
import com.meteocool.permissions.PermUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ARStormActivity : ExperimentalActivity() {
    companion object {
        const val EXTRA_TARGET = "storm_target"
        const val EXTRA_LATITUDE = "marker_latitude"
        const val EXTRA_LONGITUDE = "marker_longitude"
    }
    private lateinit var feed: StormFeed
    private lateinit var camera: StormCamera
    private lateinit var orientation: StormOrientation
    private lateinit var texture: TextureView
    private lateinit var scene: StormView
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var modes: Spinner
    private var storms = emptyList<StormData.Storm>()
    private var cells = emptyList<StormData.Cell>()
    private var clouds = emptyList<StormView.Cloud>()
    private var selection: StormData.Storm? = null
    private var cameraReady = false
    private var preview = false
    private var refreshing = false
    private var refreshRequested = false
    private var selectionRevision = 0
    private var manualRefresh: Job? = null
    private var failed = false
    private var expectedVolumes = 0
    private var selectedMode = 0
    private var requestedTarget: String? = null
    private var marker: SkyMarker? = null
    private var selectedTrack: StormData.Track? = null
    private lateinit var markerInfo: TextView
    private lateinit var markerMap: MaterialButton
    private lateinit var markerRemove: MaterialButton
    private lateinit var markButton: MaterialButton
    private lateinit var sliderValue: TextView
    private var latestStorms = emptyList<StormData.Storm>()
    private var historyScan: Long? = null
    private var historyJob: Job? = null
    private var historyLoading = false
    private var extrapolate = false
    private var alignmentMessage: String? = null
    private var mesocyclones = emptyList<StormData.Mesocyclone>()
    private var lightning = emptyList<Strike>()
    private lateinit var motionToggle: MaterialCheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!enabled) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        feed = StormFeed()
        requestedTarget = intent.getStringExtra(EXTRA_TARGET)?.takeIf { StormData.validTarget(it) }
        marker = GeoPoint(intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN), intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)).takeIf { it.valid }?.let { SkyMarker(it, null, SkyMarker.Source.MAP) }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fitInsets(outer)
        val controls = column()
        val top = LinearLayout(this)
        top.addView(button(getString(R.string.experimental_ar_close)) { finish() }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button(getString(R.string.experimental_point_menu_ar)) { requestCamera() }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(top)
        status = label(getString(R.string.experimental_ar_status_loading), 14f); controls.addView(status)
        outer.addView(controls)
        texture = TextureView(this)
        scene = StormView(this) { choose(it) }.apply { palette = (application as MeteocoolApp).prefs.radarColorMapping }
        scene.onMarked = { marked ->
            markButton.setText(R.string.experimental_ar_mark)
            if (marked == null) {
                details.setText(R.string.experimental_ar_mark_missed)
                scene.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                marker = marked
                scene.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                applyScene()
            }
        }
        val frame = FrameLayout(this).apply { addView(texture, FrameLayout.LayoutParams(-1, -1)); addView(scene, FrameLayout.LayoutParams(-1, -1)) }
        outer.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = column()
        details = label(getString(R.string.experimental_ar_accuracy), 14f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }; bottom.addView(details)
        modes = Spinner(this).apply {
            adapter = ArrayAdapter(this@ARStormActivity, android.R.layout.simple_spinner_dropdown_item, listOf(
                getString(R.string.experimental_ar_mode_live), getString(R.string.experimental_ar_mode_peel), getString(R.string.experimental_ar_mode_slice),
                getString(R.string.experimental_ar_mode_turn), getString(R.string.experimental_ar_mode_track), getString(R.string.experimental_ar_mode_floor),
                getString(R.string.experimental_ar_mode_shells), getString(R.string.experimental_ar_mode_shaft), getString(R.string.experimental_ar_mode_tops),
            ))
        }
        sliderValue = label(size = 14f)
        val slider = SeekBar(this).apply {
            max = 100; progress = 0
            contentDescription = getString(R.string.experimental_ar_mode_peel)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    scene.amount = value / 100.0
                    updateSliderValue()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        modes.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != 0 && (selection?.tier ?: 2) < 2) {
                    modes.setSelection(0)
                    details.setText(R.string.experimental_ar_label_poorly_seen)
                    return
                }
                if (position == 4 && scene.trackHeading == null) {
                    modes.setSelection(0)
                    details.setText(R.string.experimental_ar_status_failed)
                    return
                }
                selectedMode = position; slider.progress = 50; slider.isEnabled = position in 1..5
                scene.mode = StormCut.Mode.entries[position]; scene.amount = 0.5
                updateSliderValue()
                slider.contentDescription = modes.selectedItem.toString()
            }
        }
        bottom.addView(modes); bottom.addView(sliderValue); bottom.addView(slider)
        val actions = LinearLayout(this)
        actions.addView(button(getString(R.string.experimental_ar_status_storms, 0)) { listStorms() }.apply { tag = "storms" }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button(getString(R.string.experimental_ar_alignment_preview)) {
            preview = true; camera.stop(); applyScene()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(actions)
        val calibration = LinearLayout(this)
        calibration.addView(button(getString(R.string.experimental_ar_menu_reset_alignment)) { scene.resetAlignment(); alignmentMessage = null; applyScene() }, LinearLayout.LayoutParams(0, -2, 1f))
        calibration.addView(button(getString(R.string.experimental_ar_open_settings)) { appSettings() }, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(calibration)
        bottom.addView(button(getString(R.string.experimental_ar_menu_sun_fix)) { alignSun() })
        val history = LinearLayout(this)
        history.addView(button(getString(R.string.experimental_history)) { pickHistory() }, LinearLayout.LayoutParams(0, -2, 1f))
        history.addView(button(getString(R.string.experimental_latest)) {
            selectionRevision++; historyJob?.cancel(); historyScan = null; historyLoading = false
            clouds = emptyList(); selection = null; requestedTarget = null
            manualRefresh?.cancel(); manualRefresh = lifecycleScope.launch { refresh() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(history)
        motionToggle = MaterialCheckBox(this).apply {
            setText(R.string.experimental_ar_menu_extrapolate)
            setOnCheckedChangeListener { _, on -> extrapolate = on; applyScene() }
        }
        bottom.addView(motionToggle)
        bottom.addView(MaterialCheckBox(this).apply {
            setText(R.string.experimental_native_map_layer_mesocyclones); isChecked = true
            setOnCheckedChangeListener { _, on -> scene.showMesocyclones = on; scene.invalidate() }
        })
        bottom.addView(MaterialCheckBox(this).apply {
            setText(R.string.experimental_ar_menu_lightning); isChecked = true
            setOnCheckedChangeListener { _, on -> scene.showLightning = on; scene.invalidate() }
        })
        markButton = button(getString(R.string.experimental_ar_mark)) {
            scene.sunTap = null
            scene.markArmed = !scene.markArmed
            markButton.setText(if (scene.markArmed) R.string.experimental_ar_mark_cancel else R.string.experimental_ar_mark)
            details.setText(if (scene.markArmed) R.string.experimental_ar_mark_instructions else R.string.experimental_ar_mark_hint)
        }
        bottom.addView(markButton)
        bottom.addView(label(getString(R.string.experimental_ar_mark_hint), 14f))
        markerInfo = label(size = 14f); bottom.addView(markerInfo)
        val markerActions = LinearLayout(this)
        markerMap = button(getString(R.string.experimental_ar_open_on_map)) { marker?.let { openMap(it.mapSearch) } }
        markerRemove = button(getString(R.string.experimental_ar_marker_remove)) { marker = null; applyScene() }
        markerActions.addView(markerMap, LinearLayout.LayoutParams(0, -2, 1f))
        markerActions.addView(markerRemove, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(markerActions)
        outer.addView(ScrollView(this).apply { addView(bottom) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
        camera = StormCamera(texture) { available, field ->
            cameraReady = available
            if (available) scene.horizontalFov = field
            applyScene()
        }
        orientation = StormOrientation(this) { matrix, reliable ->
            if (matrix != null) scene.orientation(matrix)
            if (!reliable && ::details.isInitialized && !preview && alignmentMessage == null && scene.sunTap == null && !scene.markArmed) details.setText(R.string.experimental_ar_accuracy)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { while (true) { refresh(); delay(60_000) } }
                launch {
                    while (true) {
                        delay(10_000)
                        if (place == null || extrapolate || historyScan == null && clouds.any { !StormData.fresh(it.storm.scannedAt, System.currentTimeMillis() / 1_000.0) }) applyScene()
                    }
                }
            }
        }
        if (savedInstanceState == null) requestCamera()
    }
    private fun requestCamera() {
        preview = false
        val permissions = mutableListOf<String>()
        if (!PermUtils.isLocationPermissionGranted(this)) permissions += listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.CAMERA
        if (permissions.isNotEmpty()) permission.launch(permissions.toTypedArray()) else permissionsChanged()
    }
    override fun permissionsChanged() {
        if (!::camera.isInitialized || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (!preview) camera.start()
        manualRefresh?.cancel()
        manualRefresh = lifecycleScope.launch { refresh() }
    }
    override fun onStart() {
        super.onStart()
        if (!enabled || !::camera.isInitialized) return
        orientation.start()
        if (!preview) camera.start()
    }
    override fun onStop() {
        refreshRequested = false
        manualRefresh?.cancel()
        historyJob?.cancel(); historyLoading = false
        if (::scene.isInitialized) { scene.sunTap = null; scene.markArmed = false }
        if (::camera.isInitialized) { camera.stop(); orientation.stop() }
        super.onStop()
    }
    private suspend fun refresh() {
        if (!enabled || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (refreshing) { refreshRequested = true; return }
        refreshing = true
        val revision = selectionRevision
        try {
            locate()
            if (!enabled) return
            val now = System.currentTimeMillis() / 1_000.0
            val loaded = feed.storms(now)
            if (historyScan != null) { if (loaded != null) latestStorms = loaded; applyScene(); return }
            failed = loaded == null
            if (loaded == null) { storms = emptyList(); clouds = emptyList(); applyScene(); return }
            val here = place
            storms = loaded.filter { here == null || GeoMath.distance(here, it.point) <= 180_000 }.sortedBy { if (here == null) -(it.peakDbz ?: 0.0) else GeoMath.distance(here, it.point) }
            latestStorms = storms
            cells = feed.cells(now).orEmpty()
            mesocyclones = feed.mesocyclones(now).orEmpty()
            lightning = feed.lightning(now).orEmpty()
            if (selectionRevision != revision || historyScan != null) return
            val linkedCells = StormData.linkCells(storms, cells)
            selection = storms.firstOrNull { requestedTarget != null && (it.path == requestedTarget || linkedCells[it.path]?.code == requestedTarget) }
                ?: storms.firstOrNull { it.system == selection?.system }
                ?: marker?.let { mark -> storms.minByOrNull { GeoMath.distance(mark.point, it.point) } }
                ?: storms.firstOrNull()
            val picked = selection
            val cell = StormData.strongestCell(picked, storms, cells)
            selectedTrack = cell?.let { feed.track(it.code) }
            if (selectionRevision != revision || historyScan != null) return
            // The bounded sampled cloud preserves each selected storm's tiles; other storms remain selectable in the list.
            val whole = if (picked == null) emptyList() else storms.filter { it.system == picked.system }
            expectedVolumes = whole.size
            val candidates = whole.take(8)
            val next = mutableListOf<StormView.Cloud>()
            for (entry in candidates) {
                val volume = feed.volume(entry.path) ?: continue
                val points = withContext(Dispatchers.Default) { volume.points((24_000 / candidates.size.coerceAtLeast(1)).coerceAtLeast(1_000)) }
                next += StormView.Cloud(entry, points, volume.point, volume.coreDbz)
                if (!enabled) return
            }
            if (selectionRevision != revision || historyScan != null) return
            clouds = next
            failed = picked != null && next.isEmpty()
            applyScene()
        } finally {
            refreshing = false
            if (refreshRequested && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                refreshRequested = false
                manualRefresh = lifecycleScope.launch { refresh() }
            }
        }
    }
    private fun applyScene() {
        if (!::scene.isInitialized || !enabled) return
        val now = System.currentTimeMillis() / 1_000.0
        if (historyScan == null) clouds = clouds.filter { StormData.fresh(it.storm.scannedAt, now) }
        val here = place
        scene.cameraMode = !preview && cameraReady && ::orientation.isInitialized && orientation.available && here != null
        texture.visibility = if (scene.cameraMode) View.VISIBLE else View.INVISIBLE
        val selected = selection
        val point = here ?: (marker?.point ?: selected?.point)?.let { GeoMath.destination(it, 28_000.0, 180.0) } ?: GeoPoint(0.0, 0.0)
        val eyeAltitude = fix?.altitude?.takeIf { it.isFinite() && it in -500.0..10_000.0 && it != -1.0 && (fix?.verticalAccuracy ?: -1f) < 100 } ?: 0.0
        scene.declination = GeomagneticField(point.latitude.toFloat(), point.longitude.toFloat(), eyeAltitude.toFloat(), System.currentTimeMillis()).declination.toDouble()
        val motionCell = StormData.strongestCell(selected, storms, cells)
        scene.trackHeading = motionCell?.heading
        if ((selected?.tier ?: 2) < 2 || scene.mode == StormCut.Mode.TRACK && scene.trackHeading == null) modes.setSelection(0)
        scene.marker = marker
        scene.mesocyclones = if (historyScan == null) mesocyclones.filter { GeoMath.distance(point, it.point) <= 180_000 } else emptyList()
        scene.lightning = if (historyScan == null) lightning.filter { GeoMath.distance(point, it.point) <= 180_000 } else emptyList()
        val motionAvailable = historyScan == null && motionCell?.heading?.isFinite() == true && motionCell.speedKmh?.let { it.isFinite() && it > 0 } == true
        if (::motionToggle.isInitialized) motionToggle.isEnabled = motionAvailable
        val minutes = if (extrapolate && motionAvailable && selected != null) StormMotion.minutes(selected.scannedAt, now) else 0.0
        val placed = if (minutes > 0 && motionCell != null) clouds.map { cloud ->
            cloud.copy(centre = StormMotion.advance(cloud.centre, motionCell.heading, motionCell.speedKmh, minutes),
                storm = cloud.storm.copy(point = StormMotion.advance(cloud.storm.point, motionCell.heading, motionCell.speedKmh, minutes)))
        } else clouds
        scene.selectedCell = if (historyScan == null) motionCell else null
        scene.selectedTrack = if (historyScan == null) selectedTrack?.takeIf { it.code == motionCell?.code } else null
        scene.targetPoint = placed.firstOrNull { it.storm.path == selected?.path }?.storm?.point ?: selected?.point
        scene.scene(placed, point, eyeAltitude)
        updateSliderValue()
        if (::markerInfo.isInitialized) {
            markerInfo.visibility = if (marker == null) View.GONE else View.VISIBLE
            markerMap.visibility = markerInfo.visibility; markerRemove.visibility = markerInfo.visibility
            markerInfo.text = marker?.let { marked ->
                val distance = String.format(Locale.getDefault(), "%.1f km", GeoMath.distance(point, marked.point) / 1_000)
                val origin = when (marked.source) {
                    SkyMarker.Source.MAP -> getString(R.string.experimental_ar_marker_from_map)
                    SkyMarker.Source.ECHO -> getString(R.string.experimental_ar_marker_echo, (marked.altitude ?: 0.0) / 1_000)
                    SkyMarker.Source.ASSUMED_BASE -> getString(R.string.experimental_ar_marker_assumed, SkyMarkerRules.ASSUMED_BASE / 1_000)
                    SkyMarker.Source.GROUND -> getString(R.string.experimental_ar_marker_ground)
                }
                distance + " · " + origin
            }.orEmpty()
        }
        status.text = when {
            historyLoading -> getString(R.string.experimental_ar_status_loading)
            historyScan != null -> getString(R.string.experimental_historical, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(historyScan!! * 1_000)))
            here == null -> getString(R.string.experimental_ar_location_denied)
            failed -> getString(R.string.experimental_ar_status_failed)
            !scene.cameraMode -> getString(R.string.experimental_ar_alignment_preview)
            else -> getString(R.string.experimental_ar_status_storms, storms.map { it.system }.distinct().size)
        }
        if (selected != null) {
            details.text = getString(R.string.experimental_ar_label_age, ((now - selected.scannedAt).coerceAtLeast(0.0) / 60).toInt()) + " · " +
                String.format(Locale.getDefault(), "%.1f km", GeoMath.distance(point, selected.point) / 1_000) + "\n" +
                (alignmentMessage ?: getString(if (scene.cameraMode) R.string.experimental_ar_accuracy else R.string.experimental_volume_hint)) +
                cellDetails(motionCell) +
                (if (minutes > 0) "\n" + getString(R.string.experimental_ar_label_extrapolated, minutes.toInt()) else "") +
                if (clouds.size < expectedVolumes) "\n" + getString(R.string.experimental_partial_volumes, clouds.size, expectedVolumes) else ""
        } else details.setText(R.string.experimental_safety)
        findViewById<View>(android.R.id.content).findViewWithTag<com.google.android.material.button.MaterialButton>("storms")?.text = getString(R.string.experimental_ar_status_storms, storms.map { it.system }.distinct().size)
    }
    private fun listStorms() {
        val unique = storms.distinctBy { it.system }
        MaterialAlertDialogBuilder(this).setTitle(R.string.experimental_ar_status_loading)
            .setItems(unique.map { storm ->
                val distance = place?.let { String.format(Locale.getDefault(), "%.1f km · ", GeoMath.distance(it, storm.point) / 1_000) }.orEmpty()
                distance + (storm.peakDbz?.let { String.format(Locale.getDefault(), "%.0f dBZ", it) } ?: storm.code)
            }.toTypedArray()) { _, index -> choose(unique[index]) }
            .setNegativeButton(R.string.experimental_ar_close, null).show()
    }
    private fun updateSliderValue() {
        if (!::sliderValue.isInitialized) return
        val value = scene.amount
        sliderValue.text = when (scene.mode) {
            StormCut.Mode.PEEL -> String.format(Locale.getDefault(), "%.0f dBZ", 20 + value * (scene.peelFloor - 20))
            StormCut.Mode.SLICE -> String.format(Locale.getDefault(), "%+.1f km", value * 30 - 15)
            StormCut.Mode.TURN -> String.format(Locale.getDefault(), "%+.0f°", value * 160 - 80)
            StormCut.Mode.TRACK -> String.format(Locale.getDefault(), "%+.1f km", value * 20 - 10)
            StormCut.Mode.FLOOR -> String.format(Locale.getDefault(), "%.1f km", 1 + value * 11)
            else -> ""
        }
    }
    private fun cellDetails(cell: StormData.Cell?): String {
        if (cell == null) return ""
        val parts = mutableListOf<String>()
        selectedTrack?.takeIf { it.code == cell.code }?.place?.let(parts::add)
        cell.echoTop?.let { parts += getString(R.string.experimental_ar_label_top, it / 1_000) }
        if (cell.heading != null && cell.speedKmh != null && cell.speedKmh > 0) parts += String.format(Locale.getDefault(), "→ %s %.0f km/h", GeoMath.compass8(cell.heading), cell.speedKmh)
        cell.lightningRate?.takeIf { it > 0 }?.let { parts += getString(R.string.experimental_ar_label_lightning, it) }
        if (cell.hail) parts += getString(R.string.storm_log_hail)
        if (cell.gust) parts += getString(R.string.experimental_wind_gusts)
        if (cell.heavyRain) parts += getString(R.string.level_heavy_rain)
        if (cell.mesocyclone) parts += getString(R.string.experimental_native_map_layer_mesocyclones)
        return if (parts.isEmpty()) "" else "\n" + parts.joinToString(" · ")
    }
    private fun alignSun() {
        val point = place
        if (!scene.cameraMode || point == null) { details.setText(R.string.experimental_ar_camera_denied); return }
        val sun = SunAlignment.at(System.currentTimeMillis() / 1_000.0, point) ?: return
        if (sun.elevation <= -0.5) { details.setText(R.string.experimental_ar_sun_fix_night); return }
        scene.markArmed = false; markButton.setText(R.string.experimental_ar_mark)
        details.setText(R.string.experimental_ar_sun_fix_instructions)
        scene.sunTap = { azimuth, elevation ->
            val currentSun = SunAlignment.at(System.currentTimeMillis() / 1_000.0, point) ?: sun
            val result = SunAlignment.fix(azimuth, elevation, currentSun)
            if (result.correction != null) {
                scene.adjustForSun(result.correction)
                alignmentMessage = getString(R.string.experimental_ar_sun_fix_done, result.correction)
                applyScene()
            } else details.setText(if (result.failure == SunAlignment.Failure.BELOW_HORIZON) R.string.experimental_ar_sun_fix_night else R.string.experimental_ar_sun_fix_missed)
        }
    }
    private fun pickHistory() {
        val newest = latestStorms.mapNotNull { StormHistory.scan(it.path) }.maxOrNull() ?: return
        val scans = StormHistory.earlier(newest)
        MaterialAlertDialogBuilder(this).setTitle(R.string.experimental_history)
            .setItems(scans.map { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it * 1_000)) }.toTypedArray()) { _, index ->
                historyJob?.cancel()
                selectionRevision++; historyScan = scans[index]; historyLoading = true; motionToggle.isChecked = false
                clouds = emptyList(); applyScene()
                val listed = latestStorms.toList()
                historyJob = lifecycleScope.launch {
                    try {
                        val result = StormHistoryLoader(feed).load(scans[index], listed)
                        if (!enabled || historyScan != scans[index]) return@launch
                        clouds = result.clouds; storms = result.clouds.map { it.storm }
                        selection = storms.firstOrNull(); cells = emptyList()
                        expectedVolumes = result.available
                        historyLoading = false; applyScene()
                        if (clouds.isEmpty()) details.setText(R.string.experimental_no_scan)
                    } finally { historyLoading = false }
                }
            }.setNegativeButton(R.string.experimental_ar_close, null).show()
    }
    private fun choose(storm: StormData.Storm) {
        val cell = StormData.strongestCell(storm, storms, cells)
        MaterialAlertDialogBuilder(this).setTitle(storm.code)
            .setMessage(getString(R.string.experimental_ar_label_age, ((System.currentTimeMillis() / 1_000.0 - storm.scannedAt).coerceAtLeast(0.0) / 60).toInt()) +
                (cell?.echoTop?.let { String.format(Locale.getDefault(), "\n%.1f km", it / 1_000) } ?: ""))
            .setPositiveButton(R.string.experimental_ar_open_on_map) { _, _ -> StormData.mapSearch(storm, cell)?.let { openMap(it) } }
            .setNeutralButton(R.string.experimental_point_menu_ar) { _, _ ->
                if (historyScan != null) { selection = storm; applyScene(); return@setNeutralButton }
                selectionRevision++; requestedTarget = storm.path
                manualRefresh?.cancel()
                manualRefresh = lifecycleScope.launch { refresh() }
            }
            .setNegativeButton(R.string.experimental_ar_close, null).show()
    }
}
