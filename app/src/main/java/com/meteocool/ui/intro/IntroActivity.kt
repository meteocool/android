package com.meteocool.ui.intro

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroFragment
import com.meteocool.ui.MeteocoolActivity
import com.meteocool.R
import org.jetbrains.anko.defaultSharedPreferences

/**
 * Introduction to the user for first use.
 */
class IntroActivity : AppIntro2() {

    companion object {
        const val IS_INTRO_COMPLETED = "is_intro_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isWizardMode = true
        isSystemBackButtonLocked = true

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.intro_title1),
                description = getString(R.string.intro_description1),
                imageDrawable = R.drawable.intro_bell_512,
            )
        )

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.intro_title2),
                description = getString(R.string.intro_description2),
                imageDrawable = R.drawable.intro_jacket,
            )
        )

        /* Custom slide with notification enabling. Bell */
        addSlide(
            IntroEnableNotificationsFragment.newInstance()
        )

        /* Custom slide with privacy policy link. Umbrella (Location)  */
        addSlide(IntroPrivacyPolicyFragment.newInstance())

        addSlide(
            AppIntroFragment.newInstance(
                description = getString(R.string.intro_description5),
                imageDrawable = R.drawable.intro_satellite,
            )
        )

        addSlide(
            AppIntroFragment.newInstance(
                description = getString(R.string.intro_description6),
                imageDrawable = R.drawable.intro_settings,
            )
        )

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.intro_title7),
                description = getString(R.string.intro_description7),
                imageDrawable = R.drawable.intro_volunteers,
            )
        )

    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        defaultSharedPreferences.edit().apply {
            putBoolean(IS_INTRO_COMPLETED, true)
            apply()
        }
        startActivity(Intent(this, MeteocoolActivity::class.java))
        finish()
    }

}
