package com.meteocool.ui.intro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroFragment
import com.meteocool.ui.MeteocoolActivity
import com.meteocool.R
import com.meteocool.injection.InjectorUtils
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.map.WebViewModel
import com.vmadalin.easypermissions.EasyPermissions
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber

/**
 * Introduction to the user for first use.
 */
class IntroActivity : AppIntro2() {

    companion object {
        const val IS_INTRO_COMPLETED = "is_intro_completed"
    }

    private var isBackgroundPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isWizardMode = true
        isSystemBackButtonLocked = true

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.onboarding_title1),
                description = getString(R.string.onboarding_description1),
                imageDrawable = R.drawable.intro_sun_rain,
            )
        )

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.onboarding_title2),
                description = getString(R.string.onboarding_description2),
                imageDrawable = R.drawable.intro_jacket,
            )
        )

        /* Custom slide with notification enabling. Bell */
        addSlide(
            IntroEnableNotificationsFragment.newInstance()
        )

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.onboarding_title4),
                description = getString(R.string.onboarding_description4),
                imageDrawable = R.drawable.intro_location,
            )
        )

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.onboarding_title4),
                description = getString(R.string.onboarding_description5),
                imageDrawable = R.drawable.intro_satellite,
            )
        )

        addSlide(
            AppIntroFragment.newInstance(
                description = getString(R.string.onboarding_description6),
                imageDrawable = R.drawable.intro_settings,
            )
        )

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.onboarding_title7),
                description = getString(R.string.onboarding_description7),
                imageDrawable = R.drawable.intro_volunteers,
            )
        )

//        askForPermissions(
//            permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//            slideNumber = 4,
//            required = false
//        )

    }

    override fun onResume() {
        super.onResume()
        if (isBackgroundPermissionRequested && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED){
                Timber.d("Notifications enabled")
                defaultSharedPreferences.edit().putBoolean("notification", true).apply()
            }
        }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        isBackgroundPermissionRequested = true
                        EasyPermissions.requestPermissions(
                            this,
                            getString(R.string.bg_dialog_msg),
                            PermUtils.LOCATION_BACKGROUND,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    }
                }
            }
        }
    }
}
