package com.meteocool.ui.intro

import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.meteocool.R
import com.meteocool.databinding.IntroEnableNotificationBinding
import com.meteocool.permissions.PermUtils
import timber.log.Timber

class IntroEnableNotificationsFragment : Fragment() {
    companion object {
        fun newInstance(): IntroEnableNotificationsFragment {
            return IntroEnableNotificationsFragment()
        }
    }

    private lateinit var viewDataBinding: IntroEnableNotificationBinding
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putBoolean("notification", isGranted).apply()
                Timber.d("$isGranted")
                if (isGranted) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        requestPermissionLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    }
                } else {
                    viewDataBinding.switch1.isChecked = false
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewDataBinding =
            DataBindingUtil.inflate(inflater, R.layout.intro_enable_notification, container, false)
        return viewDataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewDataBinding.switch1.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putBoolean("notification", isChecked).apply()
            if (isChecked) {
                when {
                    (PermUtils.isBackgroundLocationPermissionGranted(requireContext())) -> {
                        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                            .putBoolean("notification", true).apply()
                    }
                    else -> {
                        val alertDialog: AlertDialog? = activity?.let {
                            val builder = AlertDialog.Builder(it)
                            builder.apply {
                                setMessage(R.string.intro_notification_dialog_explanation)
                                setPositiveButton("Ok") { _, _ ->
                                    requestPermissionLauncher.launch(
                                        ACCESS_FINE_LOCATION
                                    )
                                }
                                setNegativeButton(
                                    "Cancel"
                                ) { _, _ ->
                                    viewDataBinding.switch1.isChecked = false
                                }
                                setCancelable(false)
                            }
                            builder.create()
                        }
                        alertDialog?.show()

                    }
                }
            }
        }
    }
}