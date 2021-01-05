package com.meteocool.ui.onboard

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroFragment
import com.github.appintro.model.SliderPage
import com.meteocool.ui.MeteocoolActivity
import com.meteocool.R
import org.jetbrains.anko.defaultSharedPreferences


class OnboardingActivity : AppIntro2() {

    companion object{
        const val IS_ONBOARD_COMPLETED = "is_onboard_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sliderPage1 = SliderPage()
        sliderPage1.title = getString(R.string.onboarding_title1)
        sliderPage1.description = getString(R.string.onboarding_description1)
        sliderPage1.imageDrawable = R.drawable.volunteers_c
        sliderPage1.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        sliderPage1.titleColor = ContextCompat.getColor(this, R.color.textColor)
        sliderPage1.descriptionColor = ContextCompat.getColor(this, R.color.textColor)

        val sliderPage2 = SliderPage()
        sliderPage2.title = getString(R.string.onboarding_title2)
        sliderPage2.description = getString(R.string.onboarding_description2)
        sliderPage2.imageDrawable = R.drawable.jacket
        sliderPage2.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        sliderPage2.titleColor = ContextCompat.getColor(this, R.color.textColor)
        sliderPage2.descriptionColor = ContextCompat.getColor(this, R.color.textColor)

        val sliderPage3 = SliderPage()
        sliderPage3.title = getString(R.string.onboarding_title3)
        sliderPage3.description = getString(R.string.onboarding_description3)
        sliderPage3.imageDrawable = R.drawable.bell
        sliderPage3.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        sliderPage3.titleColor = ContextCompat.getColor(this, R.color.textColor)
        sliderPage3.descriptionColor = ContextCompat.getColor(this, R.color.textColor)

        val sliderPage4 = SliderPage()
        sliderPage4.title = getString(R.string.onboarding_title4)
        sliderPage4.description = getString(R.string.onboarding_description4)
        sliderPage4.imageDrawable = R.drawable.maps_and_location
        sliderPage4.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        sliderPage4.titleColor = ContextCompat.getColor(this, R.color.textColor)
        sliderPage4.descriptionColor = ContextCompat.getColor(this, R.color.textColor)

        val sliderPage5 = SliderPage()
        sliderPage5.title = getString(R.string.onboarding_title5)
        sliderPage5.description = getString(R.string.onboarding_description5)
        sliderPage5.imageDrawable = R.drawable.sun_rain_composit_4
        sliderPage5.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        sliderPage5.titleColor = ContextCompat.getColor(this, R.color.textColor)
        sliderPage5.descriptionColor = ContextCompat.getColor(this, R.color.textColor)


        setBarColor(ContextCompat.getColor(this, R.color.cloudAccent))
        isButtonsEnabled = false


        addSlide(AppIntroFragment.newInstance(sliderPage1))
        addSlide(AppIntroFragment.newInstance(sliderPage2))
        addSlide(AppIntroFragment.newInstance(sliderPage3))
        addSlide(AppIntroFragment.newInstance(sliderPage4))
        addSlide(AppIntroFragment.newInstance(sliderPage5))

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            askForPermissions(
                permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                slideNumber = 4,
                required = false)
        }else{
            askForPermissions(
                permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                slideNumber = 4,
                required = false)
        }

    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        defaultSharedPreferences.edit().apply {
            putBoolean(IS_ONBOARD_COMPLETED, true)
            apply()
        }
        startActivity(Intent(this, MeteocoolActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // user cannot just skip the intro once
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        startActivity(Intent(this, MeteocoolActivity::class.java))
        finish()
    }
}
