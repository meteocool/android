package com.meteocool.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.meteocool.ui.intro.IntroActivity
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

/**
 * Shows the loading screen from meteocool.
 */
class SplashActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d(isOnboardingCompleted().toString())
        if(!isOnboardingCompleted()) {
            startActivity(Intent(this.applicationContext, IntroActivity::class.java))
        }else {
            startActivity(Intent(this.applicationContext, MeteocoolActivity::class.java))
        }
        finish()
    }

    private fun isOnboardingCompleted() : Boolean {
        return defaultSharedPreferences.getBoolean(IntroActivity.IS_INTRO_COMPLETED, false)
    }
}
