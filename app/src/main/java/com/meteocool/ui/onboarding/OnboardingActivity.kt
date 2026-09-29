package com.meteocool.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.databinding.ActivityOnboardingBinding
import com.meteocool.databinding.ItemOnboardingFeatureBinding
import com.meteocool.permissions.PermUtils
import com.meteocool.push.PushSupport
import com.meteocool.ui.MeteocoolActivity

/**
 * First start: what meteocool is, then location, then rain alerts. Location
 * comes first so the "all the time" upgrade alerts need builds on it. Every
 * page can be skipped, and a skip or a denial still completes onboarding.
 */
class OnboardingActivity : AppCompatActivity() {

    private enum class Page { WELCOME, LOCATION, ALERTS }

    private class Feature(@param:DrawableRes val icon: Int, @param:StringRes val title: Int, @param:StringRes val text: Int)

    companion object {
        private const val STATE_PAGE = "page"

        private val FEATURES = listOf(
            Feature(R.drawable.ic_feature_radar, R.string.onboarding_feature_radar_title, R.string.onboarding_feature_radar_text),
            Feature(R.drawable.ic_feature_wind, R.string.onboarding_feature_nowcast_title, R.string.onboarding_feature_nowcast_text),
            Feature(R.drawable.ic_feature_bell, R.string.rain_alerts, R.string.onboarding_feature_alerts_text),
            Feature(R.drawable.ic_feature_heart, R.string.onboarding_feature_free_title, R.string.onboarding_feature_free_text),
        )
    }

    private lateinit var binding: ActivityOnboardingBinding

    // F-Droid builds cannot receive pushes, so they have no alerts page.
    private val pages = if (PushSupport.available) Page.entries else listOf(Page.WELCOME, Page.LOCATION)
    private var index = 0

    private val locationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { advance() }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            app.prefs.notification = granted && PermUtils.areNotificationsEnabled(this)
            advance()
        }

    private val backgroundLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { complete() }

    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (index > 0) show(index - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, back)

        binding.primary.setOnClickListener { primaryAction() }
        binding.secondary.setOnClickListener { advance() }
        show(savedInstanceState?.getInt(STATE_PAGE) ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, index)
    }

    private fun show(newIndex: Int) {
        index = newIndex.coerceIn(0, pages.lastIndex)
        back.isEnabled = index > 0
        val page = pages[index]
        with(binding) {
            artwork.isVisible = page == Page.WELCOME
            symbol.isVisible = page != Page.WELCOME
            features.isVisible = page == Page.WELCOME
            message.isVisible = page != Page.WELCOME
            footnote.isVisible = page == Page.ALERTS
            secondary.visibility = if (page == Page.WELCOME) View.GONE else View.VISIBLE
            when (page) {
                Page.WELCOME -> {
                    title.setText(R.string.onboarding_welcome_title)
                    primary.setText(R.string.action_continue)
                    features.removeAllViews()
                    FEATURES.forEach { feature ->
                        val row = ItemOnboardingFeatureBinding.inflate(layoutInflater, features, true)
                        row.featureIcon.setImageResource(feature.icon)
                        row.featureTitle.setText(feature.title)
                        row.featureText.setText(feature.text)
                    }
                }
                Page.LOCATION -> {
                    symbol.setImageResource(R.drawable.ic_location_active)
                    title.setText(R.string.location_access)
                    message.setText(R.string.onboarding_location_permission)
                    primary.setText(R.string.allow_location_access)
                }
                Page.ALERTS -> {
                    symbol.setImageResource(R.drawable.ic_feature_bell)
                    title.setText(R.string.rain_alerts)
                    message.setText(R.string.onboarding_notification_text)
                    footnote.setText(R.string.onboarding_settings_footnote)
                    primary.setText(R.string.tell_me_before_it_rains)
                }
            }
            scroll.scrollTo(0, 0)
        }
    }

    private fun primaryAction() {
        when (pages[index]) {
            Page.WELCOME -> advance()
            Page.LOCATION ->
                if (PermUtils.isLocationPermissionGranted(this)) advance()
                else locationLauncher.launch(PermUtils.LOCATION_PERMISSIONS)
            Page.ALERTS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && PermUtils.needsNotificationPermission(this)) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    app.prefs.notification = PermUtils.areNotificationsEnabled(this)
                    advance()
                }
        }
    }

    private fun advance() {
        if (index < pages.lastIndex) show(index + 1) else finishOnboarding()
    }

    /** Alerts while the app is closed need location "all the time", which Android asks for separately. */
    private fun finishOnboarding() {
        val needsBackground = app.prefs.notification &&
            PermUtils.isLocationPermissionGranted(this) &&
            PermUtils.needsBackgroundLocationPermission(this)
        if (!needsBackground || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            complete()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.background_location_title)
            .setMessage(R.string.background_location_rationale)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.action_not_now) { _, _ -> complete() }
            .setCancelable(false)
            .show()
    }

    private fun complete() {
        app.prefs.onboardingDone = true
        if (app.prefs.notification) app.registration.refreshRegistration()
        startActivity(Intent(this, MeteocoolActivity::class.java))
        finish()
    }
}
