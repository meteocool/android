package com.meteocool.ui.map

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.meteocool.R

/**
 * Alert pos: navigate to app permission settings (background location permission needed) or neg: cancel.
 */
class MissingLocationPermissionAlertFragment(private val msg: Int) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(msg)
                    setPositiveButton(getString(R.string.dialog_pos)) { _, _ -> }
                }
                builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}