package com.meteocool

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ListView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.navigation.NavigationView
import com.google.firebase.iid.FirebaseInstanceId
import com.meteocool.location.LocationResultHelper
import com.meteocool.location.LocationUpdatesBroadcastReceiver
import com.meteocool.location.WebAppInterface
import com.meteocool.security.Validator
import com.meteocool.settings.SettingsFragment
import com.meteocool.utility.*
import com.meteocool.view.WebViewModel
import kotlinx.android.synthetic.main.fragment_map.*
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

    private var requestingLocationUpdates = false

    /**
     * The entry point to Google Play Services.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private val webViewModel: WebViewModel by viewModels {
        InjectorUtils.provideWebViewModelFactory(this, application)
    }

    private lateinit var sFrag: SettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
//        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
//            setTheme(R.style.DarkTheme)
//        } else {
//            setTheme(R.style.LightTheme)
//        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meteocool)

        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new Instance ID token
                val token = task.result?.token

                Log.d(TAG, token)
                Toast.makeText(baseContext, token, Toast.LENGTH_SHORT).show()
            })

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (Validator.isLocationPermissionGranted(this)) {
            Log.d("Location", "Start Fused")
            requestLocationUpdates()
        } else {
            if (requestingLocationUpdates) {
                stopLocationRequests()
            }
        }

        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, WebFragment())
            .commit()

        sFrag = SettingsFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, sFrag)
            .commit()
        cancelNotifications()

        val navView: NavigationView = findViewById(R.id.nav_drawer_main)
        addClickListenerTo(navView)

        val urlObserver = androidx.lifecycle.Observer<Boolean>{

            Log.d(TAG, "permission changed $it")

        }

        webViewModel.test.observe(this, urlObserver)

    }

    private fun addClickListenerTo(navView: NavigationView) {
        navView.setNavigationItemSelectedListener { menuItem ->
            Log.d(TAG, "{${webViewModel.url.value} + before change")
            //supportFragmentManager.popBackStackImmediate()

            when (menuItem.itemId) {
                R.id.nav_home -> {
                    webViewModel.setUrlToDefault()
                    true
                }
                R.id.nav_documentation  -> {
                    val webpage: Uri = Uri.parse(WebViewModel.DOC_URL)
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
            Log.i(TAG, "Starting location updates")
            mFusedLocationClient.requestLocationUpdates(
                locationRequest, pendingIntent
            )
            requestingLocationUpdates = true

        } catch (e: SecurityException) {
            e.printStackTrace()
        }

    }

    private fun stopLocationRequests() {
        Log.i(TAG, "Stopping location updates")
        val token = defaultSharedPreferences.getString("fb", "no token")!!
        doAsync {
            NetworkUtility.sendPostRequest(
                JSONUnregisterNotification(token),
                NetworkUtility.POST_UNREGISTER_TOKEN
            )
        }
        mFusedLocationClient.removeLocationUpdates(pendingIntent)

        requestingLocationUpdates = false
    }


    override fun onStart() {
        super.onStart()
        val token = defaultSharedPreferences.getString("fb", "no token")!!
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
        cancelNotifications()
        if (webView != null) {
            webView.addJavascriptInterface(WebAppInterface(this), "Android")
        }
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
        when (key) {
            "map_rotate" -> {
                Log.i(
                    TAG,
                    "Preference value $key was updated to ${sharedPreferences!!.getBoolean(
                        key,
                        false
                    )} "
                )
                val webAppInterface = WebAppInterface(this)
                webAppInterface.requestSettings()
            }
            "map_mode" -> {
                val darkTheme = sharedPreferences!!.getBoolean(key,false)
                Log.i(TAG,"Preference value $key was updated to $darkTheme ")

                //TODO Only possible if internet connection is available
                val webAppInterface = WebAppInterface(this)
                webAppInterface.requestSettings()

                if (darkTheme) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                recreate()
            }
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
                if (isNotificationON && !requestingLocationUpdates) {
                    requestLocationUpdates()
                } else {
                    if (requestingLocationUpdates) {
                        stopLocationRequests()
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
        }
    }

    private fun cancelNotifications() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.activeNotifications.isNotEmpty()) {
            notificationManager.cancelAll()
            val token = defaultSharedPreferences.getString("fb", "no token")!!
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

    private val locationRequest: LocationRequest?
        get() {
            return LocationRequest.create()?.apply {
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
                val locationPermission =
                    (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                webViewModel.isLocationGranted.value = locationPermission
                Log.d(TAG, "RequestResult $locationPermission")
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
    }

    override fun receivedWebViewError() {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, ErrorFragment())
            .addToBackStack(null).commit()
    }
}