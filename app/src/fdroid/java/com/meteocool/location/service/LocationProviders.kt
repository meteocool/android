package com.meteocool.location.service

import android.content.Context
import com.meteocool.location.MeteocoolLocation

object LocationProviders {
    fun foreground(context: Context): ForegroundLocationService = PlatformLocationService(context)

    suspend fun currentLocation(context: Context): MeteocoolLocation? =
        PlatformLocationService.currentLocation(context)
}
