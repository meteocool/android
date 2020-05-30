package com.meteocool.utility

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.meteocool.view.WebViewModelFactory
import org.jetbrains.anko.defaultSharedPreferences

object InjectorUtils {

    private fun getSharedPreferences(context : Context) : SharedPreferences{
        return context.defaultSharedPreferences
    }

    fun provideWebViewModelFactory(context : Context, application: Application) : WebViewModelFactory{
        return WebViewModelFactory(getSharedPreferences(context), application)
    }
}
