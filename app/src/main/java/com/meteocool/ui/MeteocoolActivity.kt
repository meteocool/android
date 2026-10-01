package com.meteocool.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.BuildConfig
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.databinding.ActivityMeteocoolBinding
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.BackgroundLocationWorker
import com.meteocool.notifications.Notifications
import com.meteocool.ui.map.MapViewModel
import com.meteocool.ui.map.WebFragment

/**
 * The map, with the settings in a drawer.
 */
class MeteocoolActivity : AppCompatActivity() {

    companion object {
        /** The demo alert is shown once per launch, not on every return to the app. */
        private var demoNoticeShown = false
    }

    private lateinit var binding: ActivityMeteocoolBinding

    private val mapViewModel: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMeteocoolBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.drawerHeader.appVersion.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, bottom = bars.bottom)
            insets
        }

        val back = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    mapFragment()?.goBack() == true -> Unit
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, back)
        // The map owns horizontal drags; the drawer opens from the settings button.
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: android.view.View) {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        })

        if (savedInstanceState == null) handleNotificationTap(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationTap(intent)
    }

    private fun handleNotificationTap(intent: Intent?) {
        if (Notifications.isFromNotification(intent)) app.registration.acknowledge("notification")
    }

    private fun mapFragment(): WebFragment? = supportFragmentManager.findFragmentById(R.id.map) as? WebFragment

    fun openSettings() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun onStart() {
        super.onStart()
        BackgroundLocationWorker.cancel(this)
        app.registration.refreshAuthorization()
    }

    override fun onResume() {
        super.onResume()
        // Being in the app counts as having seen the alert.
        Notifications.clearAll(this)
        if (app.prefs.pushToken != null) app.registration.acknowledge("foreground")
        presentDemoNoticeIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        BackgroundLocationWorker.schedule(this)
    }

    /** Demo Mode replays a recorded storm; nobody should take it for today's weather. */
    private fun presentDemoNoticeIfNeeded() {
        if (demoNoticeShown || MeteocoolEnvironment.current != MeteocoolEnvironment.DEMO || !app.prefs.onboardingDone) return
        demoNoticeShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.demo_notice_title)
            .setMessage(R.string.demo_notice_message)
            .setNegativeButton(R.string.demo_notice_disable) { _, _ ->
                MeteocoolEnvironment.leaveDemo(app.prefs)
                app.registration.refreshAuthorization()
                mapViewModel.reloadMap()
            }
            .setPositiveButton(R.string.demo_notice_continue, null)
            .show()
    }
}
