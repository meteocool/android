package com.meteocool.location.service

import android.content.Context


class LocationServiceFactory {

    companion object {

        fun getLocationService(context : Context) : ForegroundLocationService {
             return FusedLocationService(context)
        }
    }
}
