package com.meteocool.view

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating a [WebViewModel] with a constructor that takes [SharedPreferences]
 * for the stored values.
 */
class WebViewModelFactory(private val defaultsharedPreferences: SharedPreferences): ViewModelProvider.NewInstanceFactory(){

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WebViewModel() as T
    }
}
