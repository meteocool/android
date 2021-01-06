package com.meteocool.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.navigation.NavigationView
import com.google.firebase.iid.FirebaseInstanceId
import com.meteocool.R
import com.meteocool.security.Validator
import com.meteocool.preferences.SettingsFragment
import com.meteocool.injection.InjectorUtils
import com.meteocool.location.service.LocationService
import com.meteocool.location.service.LocationServiceFactory
import com.meteocool.location.service.ServiceType
import com.meteocool.network.JSONClearPost
import com.meteocool.network.JSONUnregisterNotification
import com.meteocool.network.NetworkUtils
import com.meteocool.ui.map.LocationAlertFragment
import com.meteocool.ui.map.ErrorFragment
import com.meteocool.ui.map.WebFragment
import com.meteocool.view.VoidEvent
import com.meteocool.view.VoidEventObserver
import com.meteocool.view.WebViewModel
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import timber.log.Timber

/**
 * Main Activity from meteocool
 */
class MeteocoolActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener,
    WebFragment.WebViewClientListener {

    private lateinit var openDrawerObserver: VoidEventObserver<VoidEvent>
    private lateinit var backgroundLocationService: LocationService

    private val webViewModel: WebViewModel by viewModels {
        InjectorUtils.provideWebViewModelFactory(this, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meteocool)

        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Timber.w(task.exception, "getInstanceId failed")
                    return@OnCompleteListener
                }
            })

        backgroundLocationService = LocationServiceFactory.getLocationService(this, ServiceType.BACK)
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, WebFragment())
            .commit()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        cancelNotifications()

        val navView: NavigationView = findViewById(R.id.nav_drawer_main)
        addClickListenerTo(navView)

        openDrawerObserver = VoidEventObserver {
            val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
            drawerLayout.openDrawer(GravityCompat.START)
        }
        webViewModel.injectDrawer.observe(this, openDrawerObserver)

    }

    private fun addClickListenerTo(navView: NavigationView) {
        navView.setNavigationItemSelectedListener { menuItem ->
            Timber.d("{${webViewModel.url.value} + before change")

            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val drawerLayout: DrawerLayout = this.findViewById(R.id.drawer_layout)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_documentation -> {
                    val webpage: Uri = Uri.parse(NetworkUtils.DOC_URL)
                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }
    }


    /**
     * Handles the Request Updates button and requests start of location updates.
     */
    private fun requestBackgroundLocationUpdates() {
//        task.addOnFailureListener { exception ->
//            if (exception is ResolvableApiException) {
//                // Location settings are not satisfied, but this can be fixed
//                // by showing the user a dialog.
//                try {
//                    // Show the dialog by calling startResolutionForResult(),
//                    // and check the result in onActivityResult().
//                    exception.startResolutionForResult(
//                        this@MeteocoolActivity,
//                        REQUEST_CHECK_SETTINGS
//                    )
//                    Timber.d("No location permission")
//                } catch (sendEx: IntentSender.SendIntentException) {
//                    // Ignore the error.
//                }
//            }
//        }
    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        when (requestCode) {
//            REQUEST_CHECK_SETTINGS -> {
//                when (resultCode) {
//                    Activity.RESULT_OK -> {
//                    }
//                    Activity.RESULT_CANCELED -> {
//                        webViewModel.requestingBackgroundLocationUpdates(false)
//                        defaultSharedPreferences.edit().putBoolean("notification", false)
//                            .putBoolean("map_zoom", false).apply()
//                    }
//                }
//            }
//
//        }
//    }

    override fun onStart() {
        super.onStart()
        val token = defaultSharedPreferences.getString("fb_token", "no token")!!
        doAsync {
            NetworkUtils.sendPostRequest(
                JSONClearPost(
                    token,
                    "foreground"
                ),
                NetworkUtils.POST_CLEAR_NOTIFICATION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        cancelNotifications()
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        backgroundLocationService.stopLocationUpdates()
//        if (!Validator.isBackgroundLocationPermissionGranted(
//                this
//            ) && defaultSharedPreferences.getBoolean("notification", false)
//        ) {
//            defaultSharedPreferences.edit()
//                .putBoolean("notification", false)
//                .putBoolean("map_zoom", false)
//                .apply()
//            // TODO Show hint that this is not working
//        }
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        if(Validator.isBackgroundLocationPermissionGranted(this) && defaultSharedPreferences.getBoolean("notification", false)){
           backgroundLocationService.requestLocationUpdates()
        }
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Timber.d("OnSharedPref was changed $key")
        when (key) {
            "notification" -> {
                val isNotificationON = sharedPreferences!!.getBoolean(key, false)
                Timber.i("Preference value $key was updated to $isNotificationON ")
                if (!isNotificationON) {
                    val token = defaultSharedPreferences.getString("fb_token", "no token")!!
                    doAsync {
                        NetworkUtils.sendPostRequest(
                            JSONUnregisterNotification(token),
                            NetworkUtils.POST_UNREGISTER_TOKEN
                        )
                    }
                }
            }
            "map_zoom", "map_mode", "map_rotate" -> {
                webViewModel.sendSettings()
            }
        }
    }

    private fun cancelNotifications() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.activeNotifications.isNotEmpty()) {
            notificationManager.cancelAll()
            val token = defaultSharedPreferences.getString("fb_token", "no token")!!
            doAsync {
                NetworkUtils.sendPostRequest(
                    JSONClearPost(
                        token,
                        "launch_screen"
                    ),
                    NetworkUtils.POST_CLEAR_NOTIFICATION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Timber.d("$requestCode")
        permissions.forEach { Timber.d(it) }
        grantResults.forEach { Timber.d("$it") }
        when (requestCode) {
            Validator.LOCATION_BACKGROUND -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] != PackageManager.PERMISSION_GRANTED)
                ) {
                    defaultSharedPreferences.edit().putBoolean("notification", false).apply()
                    val alert = LocationAlertFragment(R.string.bg_dialog_msg)
                    alert.show(supportFragmentManager, "BackgroundLocationAlertFragment")
                }
            }
            Validator.LOCATION -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    //TODO replace with foreground
                } else {
                    defaultSharedPreferences.edit().putBoolean("map_zoom", false).apply()
                    val alert = LocationAlertFragment(R.string.gp_dialog_msg)
                    alert.show(supportFragmentManager, "LocationAlertFragment")
                }
            }
        }
    }

    override fun receivedWebViewError() {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, ErrorFragment())
            .addToBackStack(null).commit()
    }
}