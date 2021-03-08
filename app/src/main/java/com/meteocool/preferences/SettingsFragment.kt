package com.meteocool.preferences

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.CATEGORY_BROWSABLE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
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
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.annotations.AfterPermissionGranted
import timber.log.Timber
import org.jetbrains.anko.support.v4.defaultSharedPreferences

/**
 * Shows the settings from meteocool.
 */
class SettingsFragment() : PreferenceFragmentCompat() {

    private val webViewModel : WebViewModel by activityViewModels{
        InjectorUtils.provideWebViewModelFactory(requireActivity().application)
    }


    private lateinit var isZoomEnabledObserver: Observer<Boolean>
    private lateinit var areNotificationsEnabledObserver: Observer<Boolean>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        isZoomEnabledObserver = androidx.lifecycle.Observer<Boolean>{
                this.preferenceManager.findPreference<SwitchPreferenceCompat>("map_zoom")?.isChecked =
                    it
        }
        webViewModel.isZoomEnabled.observe(viewLifecycleOwner, isZoomEnabledObserver)

        areNotificationsEnabledObserver = androidx.lifecycle.Observer<Boolean>{
           findPreference<SwitchPreferenceCompat>("notification")?.isChecked = it
        }
        webViewModel.areNotificationsEnabled.observe(viewLifecycleOwner, areNotificationsEnabledObserver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPreferenceClickListener()
    }

    private fun registerPreferenceClickListener(){
        findPreference<Preference>("feedback")?.setOnPreferenceClickListener {
            handleExternalLink(getString(R.string.feedback_url) + defaultSharedPreferences.getString("fb_token", "no token")!!)
            true
        }
//        findPreference<Preference>("impressum")?.setOnPreferenceClickListener {
//            handleExternalLink(NetworkUtils.IMPRESS_URL)
//            true
//        }
        findPreference<Preference>("github")?.setOnPreferenceClickListener {
            handleExternalLink(NetworkUtils.GITHUB_URL)
            true
        }
        findPreference<Preference>("twitter")?.setOnPreferenceClickListener {
            handleExternalLink(NetworkUtils.TWITTER_URL)
            true
        }

        findPreference<SwitchPreferenceCompat>("map_zoom")?.setOnPreferenceChangeListener { preference, newValue ->
            Timber.d("$preference, $newValue")
            if(newValue.toString().toBoolean()){
                requiresLocation()
            }
            true
        }
        findPreference<SwitchPreferenceCompat>("notification")?.setOnPreferenceChangeListener { preference, newValue ->
            Timber.d("$preference, $newValue")
            if(newValue.toString().toBoolean()){
                requiresBackgroundLocation()
            }
            true
        }
    }

    private fun handleExternalLink(uri : String) {
        val link : Uri = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW, link)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        }
    }

    @AfterPermissionGranted(PermUtils.LOCATION)
    private fun requiresLocation() {
        if (EasyPermissions.hasPermissions(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)) {
            webViewModel.requestForegroundLocationUpdates()
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.gp_dialog_msg),
                PermUtils.LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    @AfterPermissionGranted(PermUtils.LOCATION_BACKGROUND)
    private fun requiresBackgroundLocation() {
        if (EasyPermissions.hasPermissions(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            // Already have permission, do the thing
            // ...
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.bg_dialog_msg),
                    PermUtils.LOCATION_BACKGROUND,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }else{
                EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.bg_dialog_msg),
                    PermUtils.LOCATION_BACKGROUND,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }
}
