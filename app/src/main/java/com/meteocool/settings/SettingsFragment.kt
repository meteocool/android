package com.meteocool.settings

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.meteocool.MeteocoolActivity
import com.meteocool.R
import com.meteocool.location.UploadLocation
import com.meteocool.location.WebAppInterface
import com.meteocool.security.Validator
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
        val permissionObserver = androidx.lifecycle.Observer<Boolean>{
                permissionChange ->
            this.preferenceManager.findPreference<SwitchPreferenceCompat>("map_zoom")?.isChecked =
                permissionChange
            Log.d("Permission", "$permissionChange")
        }
        webViewModel.isLocationGranted.observe(viewLifecycleOwner, permissionObserver)
    }
}
