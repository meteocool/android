package com.meteocool.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.*
import com.meteocool.R
import com.meteocool.databinding.ActivityMeteocoolBinding
import com.meteocool.preferences.SettingsFragment
import com.meteocool.injection.InjectorUtils
import com.meteocool.location.ListenableLocationUpdateWorker
import com.meteocool.location.service.LocationService
import com.meteocool.location.service.LocationServiceFactory
import com.meteocool.location.service.ServiceType
import com.meteocool.network.NetworkUtils
import com.meteocool.network.UploadWorker
import com.meteocool.permissions.PermUtils
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.ui.map.LocationAlertFragment
import com.meteocool.ui.map.WebViewModel
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Main Activity from meteocool
 */
class MeteocoolActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var backgroundLocationService: LocationService

    private val webViewModel: WebViewModel by viewModels {
        InjectorUtils.provideWebViewModelFactory(application)
    }

    private lateinit var binding : ActivityMeteocoolBinding

    private lateinit var appBarConfiguration: AppBarConfiguration

    private val PERIODIC_LOCATION_TAG = "location_updater"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_meteocool)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        backgroundLocationService = LocationServiceFactory.getLocationService(this, ServiceType.BACK)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        cancelNotifications()

        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_home, R.id.error), binding.drawerLayout)
        val navController = findNavController(R.id.nav_host_fragment)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navDrawerMain.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        val token = defaultSharedPreferences.getString("fb_token", "no token")!!
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = UploadWorker.createInputData(mapOf(
            Pair("url", NetworkUtils.POST_CLEAR_NOTIFICATION.toString()),
            Pair("token", token),
            Pair("from", "foreground")))

        WorkManager.getInstance(this)
            .enqueue(UploadWorker.createRequest(data))
            .result

//        backgroundLocationService.stopLocationUpdates()
        stopBackgroundWork()
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        cancelNotifications()
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        if (!PermUtils.isBackgroundLocationPermissionGranted(
                this
            ) && defaultSharedPreferences.getBoolean("notification", false)
        ) {
            defaultSharedPreferences.edit()
                .putBoolean("notification", false)
                .apply()
            val alert = LocationAlertFragment(R.string.bg_dialog_msg_deactivate)
            alert.show(supportFragmentManager, "BackgroundLocationAlertFragment")
        }
    }

    override fun onStop() {
        super.onStop()
        if(PermUtils.isBackgroundLocationPermissionGranted(this) && defaultSharedPreferences.getBoolean("notification", false)){
            startBackgroundWork()
        }
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun startBackgroundWork(){
        val uploadWorkRequest: PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ListenableLocationUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR, 5,
                    TimeUnit.MINUTES
                )
                .build()

        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(PERIODIC_LOCATION_TAG, ExistingPeriodicWorkPolicy.REPLACE, uploadWorkRequest)
    }

    private fun stopBackgroundWork(){
        WorkManager
            .getInstance(this)
            .cancelAllWorkByTag(PERIODIC_LOCATION_TAG)
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
                    val data = UploadWorker.createInputData(mapOf(
                        Pair("url",  NetworkUtils.POST_UNREGISTER_TOKEN.toString()),
                        Pair("token", SharedPrefUtils.getFirebaseToken(defaultSharedPreferences))))
                    WorkManager.getInstance(this)
                        .enqueue(UploadWorker.createRequest(data))
                        .result
                }
            }
            "map_mode", "map_rotate" -> {
                webViewModel.sendSettings()
            }
        }
    }

    private fun cancelNotifications() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.activeNotifications.isNotEmpty()) {
            notificationManager.cancelAll()
            val data = UploadWorker.createInputData(mapOf(
                Pair("url",  NetworkUtils.POST_CLEAR_NOTIFICATION.toString()),
                Pair("token", SharedPrefUtils.getFirebaseToken(defaultSharedPreferences)),
                Pair("from", "launch_screen"),
                ))
            WorkManager.getInstance(this)
                .enqueue(UploadWorker.createRequest(data))
                .result
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.d("Result $requestCode | $resultCode")
        if(resultCode == RESULT_OK){
            webViewModel.requestForegroundLocationUpdates()
        }else{
            webViewModel.stopForegroundLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
        Timber.d("$requestCode")
        permissions.forEach { Timber.d(it) }
        grantResults.forEach { Timber.d("$it") }
        when (requestCode) {
            PermUtils.LOCATION_BACKGROUND -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] != PackageManager.PERMISSION_GRANTED)
                ) {
                    defaultSharedPreferences.edit().putBoolean("notification", false).apply()
                    val alert = LocationAlertFragment(R.string.bg_dialog_msg_deactivate)
                    alert.show(supportFragmentManager, "BackgroundLocationAlertFragment")
                }
            }
            PermUtils.LOCATION -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    webViewModel.requestForegroundLocationUpdates()
                }else{
                    defaultSharedPreferences.edit().putBoolean("map_zoom", false).apply()
                    val alert = LocationAlertFragment(R.string.gp_dialog_msg)
                    alert.show(supportFragmentManager, "LocationAlertFragment")
                }
            }
        }
    }
}