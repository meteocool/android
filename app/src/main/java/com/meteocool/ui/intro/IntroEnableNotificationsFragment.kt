package com.meteocool.ui.intro

import android.Manifest.permission.*
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.meteocool.R
import com.meteocool.databinding.IntroEnableNotificationBinding
import com.meteocool.permissions.PermUtils

class IntroEnableNotificationsFragment : Fragment() {
    companion object {
        fun newInstance(): IntroEnableNotificationsFragment {
            return IntroEnableNotificationsFragment()
        }
    }

    private lateinit var viewDataBinding: IntroEnableNotificationBinding
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants: Map<String, Boolean> ->
                if (grants.values.all { it }) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (grants[ACCESS_BACKGROUND_LOCATION] == null || grants[ACCESS_BACKGROUND_LOCATION] == false) {
                            requestPermissionLauncher.launch(
                                arrayOf(ACCESS_BACKGROUND_LOCATION)
                            )
                        }
                        return@registerForActivityResult
                    }
                    updateViewAndPreferences(true)
                } else {
                    updateViewAndPreferences(false)
                }
            }
    }

    private fun updateViewAndPreferences(isGranted: Boolean) {
        requireContext().getSharedPreferences("default", MODE_PRIVATE).edit()
            .putBoolean("notification", isGranted).apply()
        viewDataBinding.switch1.isChecked = isGranted
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
            if (isChecked) {
                when {
                    (PermUtils.isNotificationPermissionGranted(requireContext()) && PermUtils.isBackgroundLocationPermissionGranted(
                        requireContext()
                    )) -> {
                        requireContext().getSharedPreferences("default", MODE_PRIVATE).edit()
                            .putBoolean("notification", true).apply()
                    }

                    else -> {
                        val alertDialog: AlertDialog? = activity?.let {
                            val builder = AlertDialog.Builder(it)
                            builder.apply {
                                setMessage(R.string.intro_notification_dialog_explanation)
                                setPositiveButton("Ok") { _, _ ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestPermissionLauncher.launch(
                                            arrayOf(ACCESS_FINE_LOCATION, POST_NOTIFICATIONS)
                                        )
                                    } else {
                                        requestPermissionLauncher.launch(
                                            arrayOf(ACCESS_FINE_LOCATION)
                                        )
                                    }
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