package com.meteocool

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.material.navigation.NavigationView
import com.google.firebase.iid.FirebaseInstanceId
import com.meteocool.location.LocationUpdatesBroadcastReceiver
import com.meteocool.location.LocationUtils
import com.meteocool.security.Validator
import com.meteocool.settings.SettingsFragment
import com.meteocool.utility.InjectorUtils
import com.meteocool.utility.JSONClearPost
import com.meteocool.utility.JSONUnregisterNotification
import com.meteocool.utility.NetworkUtils
import com.meteocool.view.EventObserver
import com.meteocool.view.VoidEvent
import com.meteocool.view.VoidEventObserver
import com.meteocool.view.WebViewModel
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import timber.log.Timber


class MeteocoolActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener, SharedPreferences.OnSharedPreferenceChangeListener,
    WebFragment.WebViewClientListener {


    private val pendingIntent: PendingIntent
        get() {
            val intent = Intent(this, LocationUpdatesBroadcastReceiver::class.java)
            intent.action = LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES
            return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /**
     * The entry point to Google Play Services.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private lateinit var openDrawerObserver: VoidEventObserver<VoidEvent>
    private lateinit var requestBackgroundLocationObserver: EventObserver<Boolean>


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

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Timber.d("Google services available: %s", isGooglePlayServicesAvailable(this).toString())

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

        requestBackgroundLocationObserver = EventObserver {
            if (it) {
                requestBackgroundLocationUpdates()
            } else {
                stopBackgroundLocationUpdates()
            }
        }
        webViewModel.requestingLocationUpdatesBackground.observe(
            this,
            requestBackgroundLocationObserver
        )
    }

    private fun isGooglePlayServicesAvailable(activity: Activity?): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val status = googleApiAvailability.isGooglePlayServicesAvailable(activity)
        if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(activity, status, 2404).show()
            }
            return false
        }
        return true
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
        try {
            val builder =
                LocationSettingsRequest.Builder()
                    .addLocationRequest(backgroundLocationRequest)
            val client: SettingsClient = LocationServices.getSettingsClient(this)
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
            task.addOnSuccessListener {
                Timber.d("Starting background location updates")
                mFusedLocationClient.requestLocationUpdates(
                    backgroundLocationRequest, pendingIntent
                )
            }

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        exception.startResolutionForResult(
                            this@MeteocoolActivity,
                            REQUEST_CHECK_SETTINGS
                        )
                        Timber.d("No location permission")
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }


        } catch (e: SecurityException) {
            e.printStackTrace()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> {
//                webViewModel.requestingBackgroundLocationUpdates(
//                    Validator.isBackgroundLocationPermissionGranted(
//                        this
//                    )
//                )
                when (resultCode) {
                    Activity.RESULT_OK -> {
                    }
                    Activity.RESULT_CANCELED -> {
                        webViewModel.requestingBackgroundLocationUpdates(false)
                        defaultSharedPreferences.edit().putBoolean("notification", false)
                            .putBoolean("map_zoom", false).apply()
                    }
                }
            }

        }
    }

    private fun stopBackgroundLocationUpdates() {
        Timber.d("Stopping location updates")
        mFusedLocationClient.removeLocationUpdates(pendingIntent)
    }


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
        webViewModel.requestingBackgroundLocationUpdates(
            Validator.isBackgroundLocationPermissionGranted(
                this
            ) && defaultSharedPreferences.getBoolean("notification", false)
        )
        cancelNotifications()
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onConnected(p0: Bundle?) {
        Timber.d("GoogleApiClient connected")
    }

    override fun onConnectionSuspended(p0: Int) {
        val text = "Connection suspended"
        Timber.w("$text: Error code: $p0")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        val text = "Exception while connecting to Google Play services"
        Timber.w("%s%s", text, connectionResult.errorMessage)
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
                }else{
                   webViewModel.requestingBackgroundLocationUpdates(
                        Validator.isBackgroundLocationPermissionGranted(
                            this
                        )
                    )
                }
            }
            "notification_intensity" -> {
                val intensity = sharedPreferences!!.getString(key, "-1")!!.toInt()
                Timber.i("Preference value $key was updated to $intensity")
                LocationUtils.NOTIFICATION_INTENSITY = intensity
                webViewModel.requestingBackgroundLocationUpdates(
                    Validator.isBackgroundLocationPermissionGranted(
                        this
                    )
                )
            }
            "notification_time" -> {
                val time = sharedPreferences!!.getString(key, "-1")!!.toInt()
                Timber.i("Preference value $key was updated to $time")
                LocationUtils.NOTIFICATION_TIME = time
                webViewModel.requestingBackgroundLocationUpdates(
                    Validator.isBackgroundLocationPermissionGranted(
                        this
                    )
                )
            }
            "map_zoom" -> {
                val isMapZoomOn = sharedPreferences!!.getBoolean(key, false)
                Timber.i("Preference value $key was updated to $isMapZoomOn ")
                if (isMapZoomOn) {
                    webViewModel.sendLocationOnce(true)
                }
                webViewModel.sendSettings()
            }
            "map_mode", "map_rotate"-> {
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

    private val backgroundLocationRequest: LocationRequest
        get() {
            return LocationRequest.create().apply {
                interval = UPDATE_INTERVAL
                fastestInterval = FASTEST_UPDATE_INTERVAL
                priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                maxWaitTime = MAX_WAIT_TIME
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
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    stopBackgroundLocationUpdates()
                    requestBackgroundLocationUpdates()
                } else {
                    defaultSharedPreferences.edit().putBoolean("notification", false).apply()
                    val alert = BackgroundLocationAlertFragment(R.string.bg_dialog_msg)
                    alert.show(supportFragmentManager, "BackgroundLocationAlertFragment")
//                    Snackbar.make(
//                        findViewById(R.id.fragmentContainer),
//                        "Location setting does not work",
//                        Snackbar.LENGTH_SHORT
//                    ).show()
                }
            }
            Validator.LOCATION -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {

                } else {
                    defaultSharedPreferences.edit().putBoolean("map_zoom", false).apply()
                    val alert = BackgroundLocationAlertFragment(R.string.gp_dialog_msg)
                    alert.show(supportFragmentManager, "BackgroundLocationAlertFragment")
                }
            }
        }
    }

    companion object {

        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private const val UPDATE_INTERVAL: Long = 15 * 60 * 1000

        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value, but they may be less frequent.
         */
        private const val FASTEST_UPDATE_INTERVAL: Long = 5 * 60 * 1000

        /**
         * The max time before batched results are delivered by location services. Results may be
         * delivered sooner than this interval.
         */
        private const val MAX_WAIT_TIME = UPDATE_INTERVAL

        const val REQUEST_CHECK_SETTINGS = 999
    }

    override fun receivedWebViewError() {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, ErrorFragment())
            .addToBackStack(null).commit()
    }
}