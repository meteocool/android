package com.meteocool.security

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import org.jetbrains.anko.defaultSharedPreferences

class Validator {
    companion object {

        const val PERMISSION_REQUEST_LOCATION = 34

        fun checkLocationPermission(context: Context, activity: Activity) {
            when {

                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED -> {
                    requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,  Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        PERMISSION_REQUEST_LOCATION
                    )
                }
            }
        }

        fun isLocationPermissionGranted(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        }
    }
}
