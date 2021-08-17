package com.meteocool.location

import android.app.Activity

class ResolvableApiException: Exception() {
    fun startResolutionForResult(activity: Activity, requestCode: Int) {}
}
