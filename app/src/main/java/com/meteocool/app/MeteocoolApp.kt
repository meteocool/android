package com.meteocool.app

import android.app.Application
import android.util.Log
import com.meteocool.BuildConfig
import com.meteocool.location.LocationRepository
import com.meteocool.location.storage.BasicLocationDatabase
import timber.log.Timber
import timber.log.Timber.DebugTree

class MeteocoolApp : Application() {

    private val database by lazy { BasicLocationDatabase.getDatabase(this)}
    private val dao by lazy {database.meteoLocationDao()}
    val repository by lazy { LocationRepository(getSharedPreferences("default", MODE_PRIVATE), dao) }


    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
            //Fabric.with(this, Crashlytics())

            //FirebaseAnalytics.getInstance(this)
        }
    }

    /** A tree which logs important information for crash reporting.  */
    private class CrashReportingTree : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?
        ) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return
            }
//            FakeCrashLibrary.log(priority, tag, message)
//            if (t != null) {
//                if (priority == Log.ERROR) {
//                    FakeCrashLibrary.logError(t)
//                } else if (priority == Log.WARN) {
//                    FakeCrashLibrary.logWarning(t)
//                }
//            }
        }
    }
}