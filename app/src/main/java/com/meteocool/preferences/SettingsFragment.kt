package com.meteocool.preferences

import android.Manifest
import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
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


    private lateinit var isZoomEnabledObserver: Observer<Boolean>
    private lateinit var areNotificationsEnabledObserver: Observer<Boolean>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestBackgroundLocationPermissionLauncher: ActivityResultLauncher<String>
    private var isBackgroundRequest = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        isZoomEnabledObserver = androidx.lifecycle.Observer<Boolean> {
            this.preferenceManager.findPreference<SwitchPreferenceCompat>("map_zoom")?.isChecked =
                it
        }
        webViewModel.isZoomEnabled.observe(viewLifecycleOwner, isZoomEnabledObserver)

        areNotificationsEnabledObserver = androidx.lifecycle.Observer<Boolean> {
            findPreference<SwitchPreferenceCompat>("notification")?.isChecked = it
        }
        webViewModel.areNotificationsEnabled.observe(
            viewLifecycleOwner,
            areNotificationsEnabledObserver
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants: Map<String, Boolean> ->
                val requiredForegroundPermissions = grants[ACCESS_FINE_LOCATION]?: false && grants[POST_NOTIFICATIONS] ?: false
                val requiredBackgroundPermissions = requiredForegroundPermissions && grants[ACCESS_BACKGROUND_LOCATION]?: false
                Timber.d("$grants")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                    grants[ACCESS_FINE_LOCATION]
                }else{

                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && grants[ACCESS_BACKGROUND_LOCATION] != null && grants[ACCESS_BACKGROUND_LOCATION] == true) {
                    requireContext().getSharedPreferences("default", MODE_PRIVATE).edit()
                        .putBoolean("notification", true).apply()
                    isBackgroundRequest = true
                } else if (grants[ACCESS_FINE_LOCATION] != null && grants.values.all { it }) {
                    Timber.d("$grants")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        requestPermissionsLauncher.launch(
                            arrayOf(ACCESS_BACKGROUND_LOCATION)
                        )
                    } else {
                        requireContext().getSharedPreferences("default", MODE_PRIVATE).edit()
                            .putBoolean("notification", true).apply()
                    }
                } else if(grants.values.all { it }){
                    isBackgroundRequest = true
                }
                else {
                    isBackgroundRequest = false
                }
            }
//        requestBackgroundLocationPermissionLauncher =
//            registerForActivityResult(
//                ActivityResultContracts.RequestPermission()
//            ) { isGranted: Boolean ->
//                Timber.d("$isGranted")
//                if (!isGranted) {
//                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                    val uri = Uri.fromParts(
//                        "package",
//                        requireActivity().packageName,
//                        null
//                    )
//                    intent.data = uri
//                    startActivity(intent)
//                }
//            }
        registerPreferenceClickListener()
    }

    private fun registerPreferenceClickListener() {
        findPreference<Preference>("feedback")?.setOnPreferenceClickListener {
            val tokenInShared = SharedPrefUtils.getFirebaseToken(requireContext().getSharedPreferences("default", MODE_PRIVATE))
            val token = FirebaseMessagingWrapper.getFirebaseToken()
            if (token == "no token") {
                handleExternalLink(
                    getString(R.string.feedback_url) + "\n" + "Token fetch failed\nShared-Token: $tokenInShared"
                )
            }

            val version: String = SharedPrefUtils.getAppVersion(requireContext().getSharedPreferences("default", MODE_PRIVATE))
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
                        requestPermissionsLauncher.launch(
                            arrayOf(ACCESS_FINE_LOCATION)
                        )
                    }
                    setNegativeButton(
                        getString(R.string.dialog_neg)
                    ) { _, _ ->
                        findPreference<SwitchPreferenceCompat>("map_zoom")?.isChecked = false
                    }
                    setCancelable(false)
                }
                builder.create()
            }
            alertDialog?.show()
        }
    }

    private fun requiresBackgroundLocationAndNotificationPermission() {
        if (!PermUtils.isNotificationPermissionGranted(requireContext()) && !PermUtils.isBackgroundLocationPermissionGranted(requireContext())) {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(R.string.dialog_msg_push)
                    setPositiveButton(getString(R.string.dialog_pos)) { _, _ ->
                        isBackgroundRequest = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionsLauncher.launch(
                                arrayOf(ACCESS_FINE_LOCATION, POST_NOTIFICATIONS)
                            )
                        } else {
                            requestPermissionsLauncher.launch(
                                arrayOf(ACCESS_FINE_LOCATION)
                            )
                        }
                    }
                    setNegativeButton(
                        getString(R.string.dialog_neg)
                    ) { _, _ ->
                        findPreference<SwitchPreferenceCompat>("notification")?.isChecked = false
                    }
                    setCancelable(false)
                }
                builder.create()
            }
            alertDialog?.show()
        }
    }
}
