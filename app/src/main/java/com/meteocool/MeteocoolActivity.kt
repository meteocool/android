package com.meteocool

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.meteocool.location.LocationResultHelper
import com.meteocool.location.LocationUpdatesBroadcastReceiver
import com.meteocool.location.WebAppInterface
import com.meteocool.security.Validator
import com.meteocool.settings.SettingsFragment
import com.meteocool.utility.InjectorUtils
import com.meteocool.utility.JSONClearPost
import com.meteocool.utility.JSONUnregisterNotification
import com.meteocool.utility.NetworkUtility
import com.meteocool.view.WebViewModel
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync


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

    private val webViewModel: WebViewModel by viewModels {
        InjectorUtils.provideWebViewModelFactory(this, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meteocool)

        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }
            })

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.d(TAG, "Google services available: " + isGooglePlayServicesAvailable(this).toString())

        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, WebFragment())
            .commit()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        cancelNotifications()

        val navView: NavigationView = findViewById(R.id.nav_drawer_main)
        addClickListenerTo(navView)

        val locationPermissionObserver = androidx.lifecycle.Observer<Boolean> {
            if (it) {
                requestLocationUpdates()
            } else {
                stopLocationRequests()
            }
        }
        webViewModel.isLocationGranted.observe(this, locationPermissionObserver)
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
            Log.d(TAG, "{${webViewModel.url.value} + before change")

            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val drawerLayout: DrawerLayout = this.findViewById(R.id.drawer_layout)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_documentation -> {
                    val webpage: Uri = Uri.parse(NetworkUtility.DOC_URL)
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
    private fun requestLocationUpdates() {
        try {
            LocationUpdatesBroadcastReceiver.sendOnce = true
            val builder =
                LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
            val client: SettingsClient = LocationServices.getSettingsClient(this)
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
            task.addOnSuccessListener {
                Log.i(TAG, "Starting location updates")
                mFusedLocationClient.requestLocationUpdates(
                    locationRequest, pendingIntent
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
                        Log.d(TAG, "No location permission")
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
        //val states = LocationSettingsStates.fromIntent(intent);
        when (requestCode) {
            REQUEST_CHECK_SETTINGS ->
                when (resultCode) {
                    Activity.RESULT_OK ->{
                        requestLocationUpdates()
                    }
                    Activity.RESULT_CANCELED ->{
                    }
                }
        }
    }

    private fun stopLocationRequests() {
        Log.i(TAG, "Stopping location updates")
        mFusedLocationClient.removeLocationUpdates(pendingIntent)
    }


    override fun onStart() {
        super.onStart()
        val token = defaultSharedPreferences.getString("fb_token", "no token")!!
        doAsync {
            NetworkUtility.sendPostRequest(
                JSONClearPost(
                    token,
                    "foreground"
                ),
                NetworkUtility.POST_CLEAR_NOTIFICATION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        requestLocationUpdates()
        cancelNotifications()
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onConnected(p0: Bundle?) {
        Log.i(TAG, "GoogleApiClient connected")
    }

    override fun onConnectionSuspended(p0: Int) {
        val text = "Connection suspended"
        Log.w(TAG, "$text: Error code: $p0")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        val text = "Exception while connecting to Google Play services"
        Log.w(TAG, text + ": " + connectionResult.errorMessage)
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
        Log.d(TAG, "OnSharedPref was changed $sharedPreferences: $key")
        when (key) {
            "map_zoom" -> {
                val zoomAfterStart = sharedPreferences!!.getBoolean(key, false)
                Log.i(TAG, "Preference value $key was updated to $zoomAfterStart ")
                Validator.checkLocationPermission(this, this)
                if (Validator.isLocationPermissionGranted(this)) {
                    val webAppInterface = WebAppInterface(this)
                    webAppInterface.requestSettings()
                }
            }
            "notification" -> {
                val isNotificationON = sharedPreferences!!.getBoolean(key, false)
                Log.i(TAG, "Preference value $key was updated to $isNotificationON ")
                Validator.checkLocationPermission(this, this)
                if (!isNotificationON) {
                    val token = defaultSharedPreferences.getString("fb_token", "no token")!!
                    doAsync {
                        NetworkUtility.sendPostRequest(
                            JSONUnregisterNotification(token),
                            NetworkUtility.POST_UNREGISTER_TOKEN
                        )
                    }
                }
            }
            "notification_intensity" -> {
                val intensity = sharedPreferences!!.getString(key, "-1")!!.toInt()
                Log.i(TAG, "Preference value $key was updated to $intensity")
                LocationResultHelper.NOTIFICATION_INTENSITY = intensity
                requestLocationUpdates()
            }
            "notification_time" -> {
                val time = sharedPreferences!!.getString(key, "-1")!!.toInt()
                Log.i(TAG, "Preference value $key was updated to $time")
                LocationResultHelper.NOTIFICATION_TIME = time
                requestLocationUpdates()
            }
            "map_rotate" -> {
                val webAppInterface = WebAppInterface(this)
                webAppInterface.requestSettings()
            }
            "map_mode" -> {
                val webAppInterface = WebAppInterface(this)
                webAppInterface.requestSettings()
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
                NetworkUtility.sendPostRequest(
                    JSONClearPost(
                        token,
                        "launch_screen"
                    ),
                    NetworkUtility.POST_CLEAR_NOTIFICATION
                )
            }
        }
    }

    private val locationRequest: LocationRequest
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
        when (requestCode) {
            Validator.PERMISSION_REQUEST_LOCATION -> {
                webViewModel.updateLocationPermission()
            }
        }
    }

    companion object {

        private val TAG = MeteocoolActivity::class.java.simpleName + "_location"

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

        private const val REQUEST_CHECK_SETTINGS = 999
    }

    override fun receivedWebViewError() {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, ErrorFragment())
            .addToBackStack(null).commit()
    }
}