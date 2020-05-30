package com.meteocool.view

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.meteocool.MeteocoolActivity
import org.jetbrains.anko.defaultSharedPreferences

class WebViewModel(private val sharedPreferences: SharedPreferences, application: Application) : AndroidViewModel(application){

    companion object {
        val test = "http://10.10.4.162:8040/"
        val prod = "https://meteocool.com/"
        const val DOC_URL = "https://meteocool.com/documentation.html"
        const val MAP_URL = "https://meteocool.com/?mobile=android2"
    }


    private val _url = MutableLiveData(getSavedMapStateOrDefault())

    var isLocationGranted = MutableLiveData<Boolean>()

    val url : LiveData<String>
        get() = _url

    fun setUrlToDefault(){
        _url.value = MAP_URL
    }

    private fun getSavedMapStateOrDefault() : String{
        return sharedPreferences.getString("map_url", null) ?: MAP_URL
    }
}
