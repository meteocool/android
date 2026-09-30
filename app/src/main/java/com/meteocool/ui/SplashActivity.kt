package com.meteocool.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.meteocool.app.DebugHooks
import com.meteocool.app.app
import com.meteocool.ui.onboarding.OnboardingActivity

/**
 * The launcher entry: onboarding on first start, the map afterwards.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugHooks.fromIntent(app, intent)
        val target = if (app.prefs.onboardingDone) MeteocoolActivity::class.java else OnboardingActivity::class.java
        // Keeps FCM's extras, so a tapped alert can be acknowledged.
        startActivity(Intent(this, target).putExtras(intent))
        finish()
    }
}
