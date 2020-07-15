package com.meteocool.view

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

class VoidEvent {
    private var hasBeenHandled = false

    fun hasBeenHandled(): Boolean = if (hasBeenHandled) {
        true
    } else {
        hasBeenHandled = true
        false
    }
}