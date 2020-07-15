package com.meteocool

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog.Builder
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment


class BackgroundLocationAlertFragment(private val msg : Int) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = Builder(it)
            builder.setMessage(msg)
                .setPositiveButton(R.string.bg_dialog_pos,
                    DialogInterface.OnClickListener { dialog, id ->
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val uri = Uri.fromParts(
                            "package",
                            requireActivity().packageName,
                            null
                        )
                        intent.data = uri
                        startActivity(intent)
                    })
                .setNegativeButton(R.string.bg_dialog_neg,
                    DialogInterface.OnClickListener { dialog, id ->

                    })
            // Create the AlertDialog object and return it
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}