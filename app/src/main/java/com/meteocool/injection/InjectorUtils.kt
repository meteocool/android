package com.meteocool.injection

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.meteocool.ui.map.WebViewModelFactory
import org.jetbrains.anko.defaultSharedPreferences

// TODO: Replace with Dagger2
object InjectorUtils {

    private fun getSharedPreferences(context : Context) : SharedPreferences{
        return context.defaultSharedPreferences
    }

    fun provideWebViewModelFactory(application: Application) : WebViewModelFactory{
        return WebViewModelFactory(application)
    }
}
