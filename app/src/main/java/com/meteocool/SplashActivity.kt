package com.meteocool

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

class SplashActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d(isOnboardingCompleted().toString())
        if(!isOnboardingCompleted()) {
            startActivity(Intent(this.applicationContext, OnboardingActivity::class.java))
        }else {
            startActivity(Intent(this.applicationContext, MeteocoolActivity::class.java))
        }
        finish()
    }

    private fun isOnboardingCompleted() : Boolean {
        return defaultSharedPreferences.getBoolean(OnboardingActivity.IS_ONBOARD_COMPLETED, false)
    }
}
