package com.meteocool.injection

import android.app.Application
import com.meteocool.ui.map.WebViewModelFactory

// TODO: Replace with Dagger2
object InjectorUtils {

    fun provideWebViewModelFactory(application: Application) : WebViewModelFactory{
        return WebViewModelFactory(application)
    }
}
