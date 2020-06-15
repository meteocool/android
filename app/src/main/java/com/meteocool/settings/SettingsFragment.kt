package com.meteocool.settings

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.meteocool.R
import com.meteocool.utility.InjectorUtils
import com.meteocool.view.WebViewModel

class SettingsFragment() : PreferenceFragmentCompat(){

    private val webViewModel : WebViewModel by activityViewModels{
        InjectorUtils.provideWebViewModelFactory(requireContext(), requireActivity().application)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val isLocationGrantedObserver = androidx.lifecycle.Observer<Boolean>{
                isLocationGranted ->
            if(!isLocationGranted) {
                this.preferenceManager.findPreference<SwitchPreferenceCompat>("map_zoom")?.isChecked =
                    false
                this.preferenceManager.findPreference<SwitchPreferenceCompat>("notification")?.isChecked =
                    false
            }
        }
        webViewModel.isLocationGranted.observe(viewLifecycleOwner, isLocationGrantedObserver)
        val prefMapRotationObserver = androidx.lifecycle.Observer<Boolean>{
                isRotationActive ->
            this.preferenceManager.findPreference<SwitchPreferenceCompat>("map_rotate")?.isChecked =
                isRotationActive
            Log.d("Map Rotation", "$isRotationActive")
        }
        webViewModel.isMapRotateActive.observe(viewLifecycleOwner, prefMapRotationObserver)
        val prefNightModeObserver = androidx.lifecycle.Observer<Boolean>{
                isNightModeEnabled ->
            this.preferenceManager.findPreference<SwitchPreferenceCompat>("map_mode")?.isChecked =
                isNightModeEnabled
            Log.d("Night Mode", "$isNightModeEnabled")
        }
        webViewModel.isNightModeEnabled.observe(viewLifecycleOwner, prefNightModeObserver)
    }
}
