package com.meteocool.preferences

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.meteocool.R
import com.meteocool.injection.InjectorUtils
import com.meteocool.network.NetworkUtils
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.map.WebViewModel
import timber.log.Timber
import org.jetbrains.anko.support.v4.defaultSharedPreferences

/**
 * Shows the settings from meteocool.
 */
class SettingsFragment() : PreferenceFragmentCompat() {

    private val webViewModel: WebViewModel by activityViewModels {
        InjectorUtils.provideWebViewModelFactory(requireActivity().application)
    }


    private lateinit var isZoomEnabledObserver: Observer<Boolean>
    private lateinit var areNotificationsEnabledObserver: Observer<Boolean>
    private lateinit var requestLocationPermissionLauncher: ActivityResultLauncher<String>
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
        requestLocationPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                Timber.d("$isGranted")
                if (isGranted) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && isBackgroundRequest) {
                        requestBackgroundLocationPermissionLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                        isBackgroundRequest = false
                    }
                }
            }
        requestBackgroundLocationPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                Timber.d("$isGranted")
                if (!isGranted) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val uri = Uri.fromParts(
                        "package",
                        requireActivity().packageName,
                        null
                    )
                    intent.data = uri
                    startActivity(intent)
                }
            }
        registerPreferenceClickListener()
    }

    private fun registerPreferenceClickListener() {
        findPreference<Preference>("feedback")?.setOnPreferenceClickListener {
            val tokenInShared = SharedPrefUtils.getFirebaseToken(defaultSharedPreferences)
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    handleExternalLink(
                        getString(R.string.feedback_url) +  "\n" + "Token fetch failed\nShared-Token: $tokenInShared"
                    )
                    return@OnCompleteListener
                }

                val token = task.result
                handleExternalLink(
                    getString(R.string.feedback_url) +  "\n" + token +"\nShared-Token: $tokenInShared"
                )
            })
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
                putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=com.meteocool")
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
                requiresBackgroundLocation()
            }
            true
        }
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
                    setPositiveButton(getString(R.string.bg_dialog_pos)) { _, _ ->
                        requestLocationPermissionLauncher.launch(
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    }
                    setNegativeButton(
                        getString(R.string.bg_dialog_neg)
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

    private fun requiresBackgroundLocation() {
        if (!PermUtils.isBackgroundLocationPermissionGranted(requireContext())) {
            val alertDialog: AlertDialog? = activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(R.string.dialog_msg_push)
                    setPositiveButton(getString(R.string.bg_dialog_pos)) { _, _ ->
                        isBackgroundRequest = true
                        requestLocationPermissionLauncher.launch(
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    }
                    setNegativeButton(
                        getString(R.string.bg_dialog_neg)
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
