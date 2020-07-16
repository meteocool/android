package com.meteocool.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.meteocool.R
import com.meteocool.security.Validator
import com.meteocool.utility.InjectorUtils
import com.meteocool.utility.NetworkUtils
import com.meteocool.view.EventObserver
import com.meteocool.view.WebViewModel
import timber.log.Timber
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class SettingsFragment() : PreferenceFragmentCompat() {

    private val webViewModel : WebViewModel by activityViewModels{
        InjectorUtils.provideWebViewModelFactory(requireContext(), requireActivity().application)
    }


    private lateinit var isZoomEnabledObserver: Observer<Boolean>
    private lateinit var areNotificationsEnabledObserver: Observer<Boolean>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val isZoomEnabledObserver = androidx.lifecycle.Observer<Boolean>{
                this.preferenceManager.findPreference<SwitchPreferenceCompat>("map_zoom")?.isChecked =
                    it
        }
        webViewModel.isZoomEnabled.observe(viewLifecycleOwner, isZoomEnabledObserver)

        val areNotificationsEnabledObserver = androidx.lifecycle.Observer<Boolean>{
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
            val webpage: Uri = Uri.parse(NetworkUtils.FEEDBACK_URL + defaultSharedPreferences.getString("fb_token", "no token")!!)
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            }
            true
        }
        findPreference<Preference>("impressum")?.setOnPreferenceClickListener {
            val webpage: Uri = Uri.parse(NetworkUtils.IMPRESSUM_URL)
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            }
            true
        }
        findPreference<Preference>("github")?.setOnPreferenceClickListener {
            val webpage: Uri = Uri.parse(NetworkUtils.GITHUB_URL)
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            }
            true
        }
        findPreference<Preference>("twitter")?.setOnPreferenceClickListener {
            val webpage: Uri = Uri.parse(NetworkUtils.TWITTER_URL)
            val intent = Intent(Intent.ACTION_VIEW, webpage)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            }
            true
        }

        findPreference<SwitchPreferenceCompat>("map_zoom")?.setOnPreferenceChangeListener { preference, newValue ->
            Timber.d("$preference, $newValue")
            if(newValue.toString().toBoolean() && !Validator.isLocationPermissionGranted(requireContext())){
                Validator.checkLocationPermission(requireContext(), requireActivity())
            }
            true
        }
        findPreference<SwitchPreferenceCompat>("notification")?.setOnPreferenceChangeListener { preference, newValue ->
            Timber.d("$preference, $newValue")
            val value = newValue.toString().toBoolean()
            if(value && !Validator.isBackgroundLocationPermissionGranted(requireContext())){
                Validator.checkBackgroundLocationPermission(requireContext(), requireActivity())
            }
            true
        }
    }
}
