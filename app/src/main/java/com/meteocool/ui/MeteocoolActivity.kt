package com.meteocool.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
import com.meteocool.firebase.MyFirebaseMessagingService
import com.meteocool.preferences.SettingsFragment
import com.meteocool.injection.InjectorUtils
import com.meteocool.location.ListenableLocationUpdateWorker
import com.meteocool.network.NetworkUtils
import com.meteocool.network.UploadWorker
import com.meteocool.permissions.PermUtils
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.ui.map.WebViewModel
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Main Activity from meteocool
 */
class MeteocoolActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {


    private val webViewModel: WebViewModel by viewModels {
        InjectorUtils.provideWebViewModelFactory(application)
    }

    private lateinit var binding: ActivityMeteocoolBinding

    private lateinit var appBarConfiguration: AppBarConfiguration

    private val PERIODIC_LOCATION_TAG = "location_updater"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_meteocool)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        storeAppVersionInSharedPref()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()

        appBarConfiguration =
            AppBarConfiguration(setOf(R.id.nav_home, R.id.error), binding.drawerLayout)
        val navController = findNavController(R.id.nav_host_fragment)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navDrawerMain.setupWithNavController(navController)


        val uploadLocation = UploadWorker.createRequest(
            UploadWorker.createDataForLocationPost(
                defaultSharedPreferences,
                SharedPrefUtils.getSavedLocationResult(defaultSharedPreferences)
            )
        )
        WorkManager.getInstance(this)
            .enqueue(uploadLocation)
            .result

    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        stopBackgroundWork()
        if (!PermUtils.isLocationPermissionGranted(
                this
            ) && defaultSharedPreferences.getBoolean("map_zoom", false)
        ) {
            this.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(getString(R.string.dialog_msg_negative_info_map_zoom))
                    openSettings()
                    setNegativeButton(getString(R.string.dg_neg_map_zoom)) { _, _ ->
                        defaultSharedPreferences.edit().putBoolean("map_zoom", false).apply()
                    }
                }
                builder.create()
            }.show()
        }
        if (!PermUtils.isBackgroundLocationPermissionGranted(
                this
            ) && defaultSharedPreferences.getBoolean("notification", false)
        ) {
            this.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(getString(R.string.dialog_msg_negative_info_map_zoom))
                    openSettings()
                    setNegativeButton(getString(R.string.dg_neg_map_zoom)) { _, _ ->
                        defaultSharedPreferences.edit().putBoolean("notification", false).apply()
                    }
                }
                builder.create()
            }.show()
        }
    }

    private fun AlertDialog.Builder.openSettings() {
        setPositiveButton(getString(R.string.dg_settings)) { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val uri = Uri.fromParts(
                "package",
                packageName,
                null
            )
            intent.data = uri
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        if (defaultSharedPreferences.getBoolean("notification", false)) {
            MyFirebaseMessagingService.cancelNotification(this, "foreground")
        }
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        if (PermUtils.isBackgroundLocationPermissionGranted(this) && defaultSharedPreferences.getBoolean(
                "notification",
                false
            )
        ) {
            startBackgroundWork()
        }
    }

    override fun onPause() {
        super.onPause()
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun startBackgroundWork() {
        Timber.d("Start background work")
        val uploadWorkRequest: PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ListenableLocationUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .build()

        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(
                PERIODIC_LOCATION_TAG,
                ExistingPeriodicWorkPolicy.REPLACE,
                uploadWorkRequest
            )
    }

    private fun stopBackgroundWork() {
        Timber.d("Stop background work")
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
                    val data = UploadWorker.createInputData(
                        mapOf(
                            Pair("url", NetworkUtils.POST_UNREGISTER_TOKEN.toString()),
                            Pair(
                                "token",
                                SharedPrefUtils.getFirebaseToken(defaultSharedPreferences)
                            )
                        )
                    )
                    WorkManager.getInstance(this)
                        .enqueue(UploadWorker.createRequest(data))
                        .result
                } else {
                    val data = UploadWorker.createDataForLocationPost(
                        defaultSharedPreferences,
                        SharedPrefUtils.getSavedLocationResult(defaultSharedPreferences)
                    )
                    WorkManager.getInstance(this)
                        .enqueue(UploadWorker.createRequest(data))
                        .result
                }
            }
            "notification_details", "notification_intensity", "notification_time" -> {
                val data = UploadWorker.createDataForLocationPost(
                    defaultSharedPreferences,
                    SharedPrefUtils.getSavedLocationResult(defaultSharedPreferences)
                )
                WorkManager.getInstance(this)
                    .enqueue(UploadWorker.createRequest(data))
                    .result
            }
            "map_rotate" -> {
                webViewModel.sendSettings()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.d("Result $requestCode | $resultCode")
        if (resultCode == RESULT_OK) {
            webViewModel.requestForegroundLocationUpdates()
        } else {
            webViewModel.stopForegroundLocationUpdates()
        }
    }

    private fun storeAppVersionInSharedPref() {
        try {
            val pInfo = packageManager.getPackageInfo(
                packageName,
                0
            )
            SharedPrefUtils.saveAppVersion(defaultSharedPreferences, pInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }
}