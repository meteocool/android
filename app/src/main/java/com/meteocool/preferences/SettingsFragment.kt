package com.meteocool.preferences

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import androidx.core.net.toUri
import androidx.core.text.inSpans
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.BuildConfig
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.permissions.PermUtils
import com.meteocool.push.PushSupport
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The settings, shown in the map's drawer: rain alerts, the map's
 * appearance, about, and the data sources the map credits.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private class DataSource(val name: String, @param:StringRes val detail: Int, val url: String)

    companion object {
        private const val GITHUB_URL = "https://github.com/meteocool/android"
        private const val PRIVACY_URL = "https://meteocool.com/privacy.html"
        private const val SUPPORT_EMAIL = "support@meteocool.com"

        /**
         * The app hides the web map's attribution, so this list is the only
         * credit. Keep it in step with core's layers/attributions.ts and the
         * imprint's #data section.
         */
        private val DATA_SOURCES = listOf(
            DataSource("© Deutscher Wetterdienst (DWD)", R.string.source_dwd, "https://www.dwd.de/EN/service/copyright/copyright_node.html"),
            DataSource("© MeteoSwiss", R.string.source_meteoswiss, "https://opendatadocs.meteoswiss.ch/general/terms-of-use"),
            DataSource("© Météo-France", R.string.source_meteofrance, "https://www.etalab.gouv.fr/licence-ouverte-open-licence/"),
            DataSource("© Český hydrometeorologický ústav (ČHMÚ)", R.string.source_chmi, "https://www.chmi.cz/o-chmu/caste-dotazy-faq/open-data"),
            DataSource("© IMGW-PIB", R.string.source_imgw, "https://danepubliczne.imgw.pl/regulations"),
            DataSource("NOAA / National Weather Service", R.string.source_noaa, "https://www.weather.gov/disclaimer"),
            DataSource("© Blitzortung.org", R.string.source_blitzortung, "https://www.blitzortung.org/"),
            DataSource("© Open-Meteo.com", R.string.source_openmeteo, "https://open-meteo.com/en/licence"),
            DataSource("Copernicus Sentinel · © OroraTech", R.string.source_copernicus, "https://sentinels.copernicus.eu/documents/247904/690755/Sentinel_Data_Legal_Notice"),
            DataSource("© OpenStreetMap contributors", R.string.source_osm, "https://www.openstreetmap.org/copyright"),
            DataSource("© Protomaps", R.string.source_protomaps, "https://protomaps.com"),
            DataSource("Freepik · Flaticon", R.string.source_freepik, "https://www.flaticon.com"),
        )
    }

    private val prefs get() = requireContext().app.prefs
    private val registration get() = requireContext().app.registration

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && PermUtils.areNotificationsEnabled(requireContext())) enableAlertsWithLocation()
            else showPermissionHelp()
        }

    private val alertsLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) requestBackgroundLocation() else showPermissionHelp()
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            registration.refreshRegistration()
            updateNotificationsFooter()
        }

    private val autoZoomLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants.values.any { it }
            findPreference<SwitchPreferenceCompat>(Prefs.MAP_ZOOM)?.isChecked = granted
            if (!granted) showLocationHelp()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<PreferenceCategory>("notifications_category")?.isVisible = PushSupport.available
        setUpNotifications()
        setUpMap()
        setUpAbout()
        setUpDataSources()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                registration.syncFailed.collect { updateNotificationsFooter() }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MeteocoolEnvironment.changes.collect {
                    findPreference<Preference>("mode")?.summary = getString(modeTitle(it))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have changed in system settings meanwhile.
        findPreference<SwitchPreferenceCompat>(Prefs.NOTIFICATION)?.isChecked = prefs.notification
        findPreference<SwitchPreferenceCompat>(Prefs.MAP_ZOOM)?.isChecked = prefs.mapZoom
        updateNotificationsFooter()
    }

    /* ---- rain alerts --------------------------------------------------- */

    private fun setUpNotifications() {
        findPreference<SwitchPreferenceCompat>(Prefs.NOTIFICATION)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                enableAlerts()
            } else {
                registration.disable()
                updateNotificationsFooter()
            }
            // enableAlerts() flips the switch itself once permission is there.
            newValue == false
        }
    }

    /** Notifications first, then location, then location while the app is closed. */
    private fun enableAlerts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && PermUtils.needsNotificationPermission(requireContext())) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!PermUtils.areNotificationsEnabled(requireContext())) {
            showPermissionHelp()
        } else {
            enableAlertsWithLocation()
        }
    }

    private fun enableAlertsWithLocation() {
        findPreference<SwitchPreferenceCompat>(Prefs.NOTIFICATION)?.isChecked = true
        updateNotificationsFooter()
        if (PermUtils.isLocationPermissionGranted(requireContext())) {
            requestBackgroundLocation()
        } else {
            alertsLocationLauncher.launch(PermUtils.LOCATION_PERMISSIONS)
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !PermUtils.needsBackgroundLocationPermission(requireContext())) {
            registration.refreshRegistration()
            updateNotificationsFooter()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.background_location_title)
            .setMessage(R.string.background_location_rationale)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.action_not_now) { _, _ ->
                registration.refreshRegistration()
                updateNotificationsFooter()
            }
            .show()
    }

    /** What the section's footer says, most urgent first. */
    private fun updateNotificationsFooter() {
        val footer = findPreference<Preference>("notifications_footer") ?: return
        val context = context ?: return
        val enabled = prefs.notification
        val permissionsMissing = !PermUtils.areNotificationsEnabled(context) ||
            !PermUtils.isBackgroundLocationPermissionGranted(context)
        footer.setSummary(
            when {
                registration.syncFailed.value -> R.string.notification_sync_failed
                enabled && permissionsMissing -> R.string.notification_permissions_help
                enabled -> R.string.meteorological_details_help
                else -> R.string.notifications_explanation
            }
        )
    }

    private fun showPermissionHelp() {
        findPreference<SwitchPreferenceCompat>(Prefs.NOTIFICATION)?.isChecked = prefs.notification
        updateNotificationsFooter()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notification_permissions_title)
            .setMessage(R.string.notification_permissions_help)
            .setPositiveButton(R.string.change_in_settings) { _, _ -> startActivity(PermUtils.appSettingsIntent(requireContext())) }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    private fun showLocationHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_permission_required)
            .setMessage(R.string.location_permission_general)
            .setPositiveButton(R.string.change_in_settings) { _, _ -> startActivity(PermUtils.appSettingsIntent(requireContext())) }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    /* ---- map ------------------------------------------------------------ */

    private fun setUpMap() {
        findPreference<SwitchPreferenceCompat>(Prefs.MAP_ZOOM)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true && !PermUtils.isLocationPermissionGranted(requireContext())) {
                autoZoomLocationLauncher.launch(PermUtils.LOCATION_PERMISSIONS)
                false
            } else {
                true
            }
        }

        // "Match System" also says which of the two it currently is.
        findPreference<ListPreference>(Prefs.BASE_LAYER)?.summaryProvider =
            Preference.SummaryProvider<ListPreference> { preference ->
                val entry = preference.entry ?: getString(R.string.basemap_system)
                if (preference.value == "system" || preference.value == null) {
                    val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                    // Mid-sentence: "Currently light", "Derzeit dunkel".
                    val current = getString(if (dark) R.string.basemap_dark else R.string.basemap_light)
                        .lowercase(ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault())
                    "$entry · ${getString(R.string.system_detail, current)}"
                } else {
                    entry
                }
            }
    }

    /* ---- about ---------------------------------------------------------- */

    private fun setUpAbout() {
        findPreference<Preference>("github")?.setOnPreferenceClickListener { open(GITHUB_URL) }
        findPreference<Preference>("privacy")?.setOnPreferenceClickListener { open(PRIVACY_URL) }
        findPreference<Preference>("feedback")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject, BuildConfig.VERSION_NAME))
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_body))
            launch(intent)
        }
        findPreference<Preference>("share")?.setOnPreferenceClickListener {
            val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, BuildConfig.STORE_URL).setType("text/plain")
            launch(Intent.createChooser(send, null))
        }
        findPreference<Preference>("version")?.title = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        findPreference<Preference>("mode")?.setOnPreferenceClickListener {
            showModePicker()
            true
        }
    }

    @StringRes
    private fun modeTitle(environment: MeteocoolEnvironment): Int = when (environment) {
        MeteocoolEnvironment.APP -> R.string.mode_production
        MeteocoolEnvironment.STAGING -> R.string.experimental_features
        MeteocoolEnvironment.DEMO -> R.string.mode_demo
    }

    @StringRes
    private fun modeDetail(environment: MeteocoolEnvironment): Int = when (environment) {
        MeteocoolEnvironment.APP -> R.string.mode_production_detail
        MeteocoolEnvironment.STAGING -> R.string.mode_experimental_detail
        MeteocoolEnvironment.DEMO -> R.string.mode_demo_detail
    }

    /**
     * Mode: the deployment the app talks to. A tap moves the selection, OK
     * applies it at once, without a restart: the map reloads and a push
     * registration moves to the new deployment.
     */
    private fun showModePicker() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        // The dialog's theme, for the padding and colours of the view below.
        val context = builder.context
        var selection = MeteocoolEnvironment.current
        val secondary = MaterialColors.getColor(context, android.R.attr.textColorSecondary, 0)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_choices, null)
        val choices = view.findViewById<RadioGroup>(R.id.choices)
        MeteocoolEnvironment.entries.forEach { environment ->
            val choice = inflater.inflate(R.layout.dialog_choice, choices, false) as RadioButton
            choice.id = View.generateViewId()
            choice.text = SpannableStringBuilder(getString(modeTitle(environment))).append("\n").apply {
                inSpans(ForegroundColorSpan(secondary), RelativeSizeSpan(0.875f)) { append(getString(modeDetail(environment))) }
            }
            choices.addView(choice)
            if (environment == selection) choices.check(choice.id)
            choice.setOnCheckedChangeListener { _, checked -> if (checked) selection = environment }
        }
        view.findViewById<TextView>(R.id.footer).setText(R.string.mode_footer)
        builder
            .setTitle(R.string.mode)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> MeteocoolEnvironment.select(selection, prefs) }
            .show()
    }

    /* ---- data sources --------------------------------------------------- */

    private fun setUpDataSources() {
        val category = findPreference<PreferenceCategory>("data_sources") ?: return
        val context = preferenceManager.context
        DATA_SOURCES.forEach { source ->
            category.addPreference(Preference(context).apply {
                title = source.name
                setSummary(source.detail)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceClickListener { open(source.url) }
            })
        }
        category.addPreference(Preference(context).apply {
            layoutResource = R.layout.preference_footer
            setSummary(R.string.data_sources_footer)
            isSelectable = false
        })
    }

    private fun open(url: String): Boolean = launch(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun launch(intent: Intent): Boolean {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Nothing on the device handles it; nothing to do.
        }
        return true
    }
}
