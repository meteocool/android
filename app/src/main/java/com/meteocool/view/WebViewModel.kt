package com.meteocool.view

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WebViewModel() : ViewModel(){

    companion object {
        const val MAP_URL = "https://meteocool.com/?mobile=android2"
        const val DOC_URL = "https://meteocool.com/documentation.html"
    }


    val _url = MutableLiveData<String>()

    /*val url: LiveData<String>
        get() = _url*/

    fun initialStart(){
        _url.value = MAP_URL
        /*val lastState = sharedPreferences.getString("map_url", null)
        if(lastState != null){
            _url.value = lastState
        }*/
    }

    fun changeURL(newUrl : String){
        _url.value = newUrl
    }

}
