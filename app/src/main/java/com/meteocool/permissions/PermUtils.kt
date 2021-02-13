package com.meteocool.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import com.meteocool.R
import com.meteocool.ui.map.LocationAlertFragment
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.annotations.AfterPermissionGranted

/**
 * Helper class for validating app permission constraints.
 */
class PermUtils {
    companion object {

        const val LOCATION = 10
        const val LOCATION_BACKGROUND = 11

        fun checkLocationPermission(context: Context, activity: Activity) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {

                }
                shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) -> {
                    val alert = LocationAlertFragment(R.string.gp_dialog_msg)
//                    alert.show(/, "LocationAlertFragment")
            }
                else -> {
                    requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION
                    )
            }
            }
        }

        fun checkBackgroundLocationPermission(context: Context, activity: Activity) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        LOCATION_BACKGROUND
                    )
                } else {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                    requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ),
                        LOCATION_BACKGROUND
                    )
                }
            }
        }
    }

    fun isLocationPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBackgroundLocationPermissionGranted(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && isLocationPermissionGranted(context)
        } else {
            isLocationPermissionGranted(context)
        }
    }

}
}
