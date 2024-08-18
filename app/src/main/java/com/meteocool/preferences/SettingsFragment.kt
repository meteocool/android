package com.meteocool.preferences

import android.Manifest.permission.*
import android.app.Application.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.meteocool.R
import com.meteocool.injection.InjectorUtils
import com.meteocool.network.NetworkUtils
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.map.WebViewModel
import timber.log.Timber


/**
 * Shows the settings from meteocool.
 */
class SettingsFragment() : PreferenceFragmentCompat() {

    private val webViewModel: WebViewModel by activityViewModels {
        InjectorUtils.provideWebViewModelFactory(requireActivity().application)
    }


    private lateinit var mapZoomPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationsPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PreferenceManager.getDefaultSharedPreferences(requireActivity())
            .registerOnSharedPreferenceChangeListener(webViewModel)
        webViewModel.isZoomEnabled.observe(viewLifecycleOwner, Observer { isZoomEnabled ->
            Timber.i("isZoomEnabled: $isZoomEnabled")
            findPreference<SwitchPreferenceCompat>("map_zoom")?.isChecked = isZoomEnabled
        })

        webViewModel.areNotificationsEnabled.observe(viewLifecycleOwner, Observer { areNotificationsEnabled ->
            Timber.i("areNotificationsEnabled: $areNotificationsEnabled")
            findPreference<SwitchPreferenceCompat>("notification")?.isChecked = areNotificationsEnabled
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationsPermissionsLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants: Map<String, Boolean> ->
                if (grants.values.all { it }) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (grants[ACCESS_BACKGROUND_LOCATION] == null || grants[ACCESS_BACKGROUND_LOCATION] == false) {
                            notificationsPermissionsLauncher.launch(
                                arrayOf(ACCESS_BACKGROUND_LOCATION)
                            )
                        } else {
                            PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
                                .putBoolean("notification", true).apply()
                        }
                        return@registerForActivityResult
                    }
                    PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
                        .putBoolean("notification", true).apply()
                } else {
                    PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
                        .putBoolean("notification", false).apply()
                }
            }

        mapZoomPermissionsLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants: Map<String, Boolean> ->
                if (grants.values.all { it }) {
                    PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
                        .putBoolean("map_zoom", true).apply()
                } else {
                    PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
                        .putBoolean("map_zoom", false).apply()
                }
            }
        registerPreferenceClickListener()
    }

    private fun registerPreferenceClickListener() {
        findPreference<Preference>("feedback")?.setOnPreferenceClickListener {
            val tokenInShared =
                SharedPrefUtils.getFirebaseToken(requireContext().getSharedPreferences("default", MODE_PRIVATE))
            val token = FirebaseMessagingWrapper.getFirebaseToken()
            if (token == "no token") {
                handleExternalLink(
                    getString(R.string.feedback_url) + "\n" + "Token fetch failed\nShared-Token: $tokenInShared"
                )
            }

            val version: String =
                SharedPrefUtils.getAppVersion(requireContext().getSharedPreferences("default", MODE_PRIVATE))
            handleExternalLink(
                getString(R.string.feedback_url) + "\n" + token + "\nShared-Token: $tokenInShared" + "\n" + version

            )
            true
        }
        findPreference<Preference>("github")?.setOnPreferenceClickListener {
            handleExternalLink(NetworkUtils.GITHUB_URL)
            true
        }
        findPreference<Preference>("twitter")?.setOnPreferenceClickListener {
            handleExternalLink(NetworkUtils.TWITTER_URL)
            true
        }

        findPreference<Preference>("privacy")?.setOnPreferenceClickListener {
            handleExternalLink(NetworkUtils.PRIVACY_URL)
            true
        }

        findPreference<Preference>("share")?.setOnPreferenceClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    "https://play.google.com/store/apps/details?id=com.meteocool"
                )
                type = "text/html"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)

            true
        }

        findPreference<SwitchPreferenceCompat>("map_zoom")?.setOnPreferenceChangeListener { preference, newValue ->
            Timber.d("$preference, $newValue")
            if (newValue.toString().toBoolean()) {
                requiresLocation()
            }
            true
        }
        findPreference<SwitchPreferenceCompat>("notification")?.setOnPreferenceChangeListener { preference, newValue ->
            Timber.d("$preference, $newValue")
            if (newValue.toString().toBoolean()) {
                requiresBackgroundLocationAndNotificationPermission()
            }
            true
        }
    }

    private fun getAppVersion(): String {
        var version: String = ""
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            )
            version = pInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return version
    }

    private fun handleExternalLink(uri: String) {
        val link: Uri = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW, link)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun requiresLocation() {
        if (PermUtils.isLocationPermissionGranted(requireContext())) {
            webViewModel.requestForegroundLocationUpdates()
        } else {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(R.string.dialog_msg_map_zoom)
                    setPositiveButton(getString(R.string.dialog_pos)) { _, _ ->
                        mapZoomPermissionsLauncher.launch(
                            arrayOf(ACCESS_FINE_LOCATION)
                        )
                    }
                    setNegativeButton(
                        getString(R.string.dialog_neg)
                    ) { _, _ ->
                        PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
                            .putBoolean("map_zoom", false).apply()
                    }
                    setCancelable(false)
                }
                builder.create()
            }
            alertDialog?.show()
        }
    }

    private fun requiresBackgroundLocationAndNotificationPermission() {
        if (!(PermUtils.isNotificationPermissionGranted(requireContext()) && PermUtils.isBackgroundLocationPermissionGranted(
                requireContext()
            ))
        ) {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(R.string.dialog_msg_push)
                    setPositiveButton(getString(R.string.dialog_pos)) { _, _ ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationsPermissionsLauncher.launch(
                                arrayOf(ACCESS_FINE_LOCATION, POST_NOTIFICATIONS)
                            )
                        } else {
                            notificationsPermissionsLauncher.launch(
                                arrayOf(ACCESS_FINE_LOCATION)
                            )
                        }
                    }
                    setNegativeButton(
                        getString(R.string.dialog_neg)
                    ) { _, _ ->
                        PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit()
                            .putBoolean("notification", false).apply()
                    }
                    setCancelable(false)
                }
                builder.create()
            }
            alertDialog?.show()
        } else {
            webViewModel.requestForegroundLocationUpdates()
        }
    }
}
