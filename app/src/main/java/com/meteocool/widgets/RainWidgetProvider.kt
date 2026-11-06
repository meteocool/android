package com.meteocool.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

open class RainWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        schedule(context)
        ids.forEach { refresh(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id in ids(context)) refresh(context, id)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context, id)

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach {
            WidgetStore.delete(context, it)
            WorkManager.getInstance(context).cancelUniqueWork("widget-$it")
        }
    }

    override fun onDisabled(context: Context) {
        if (ids(context).isEmpty()) WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    companion object {
        const val REFRESH = "com.meteocool.widgets.REFRESH"
        private const val PERIODIC = "rain-widgets"
        private val providers = listOf(RadarWidgetProvider::class.java, ForecastWidgetProvider::class.java,
            ClockWidgetProvider::class.java, PlacesWidgetProvider::class.java)

        fun kind(className: String?): WidgetKind? = providers.indexOfFirst { it.name == className }
            .takeIf { it >= 0 }?.let { WidgetKind.entries[it] }

        fun ids(context: Context): List<Int> = providers.flatMap {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, it)).toList()
        }

        fun schedule(context: Context) {
            if (ids(context).isEmpty()) return
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES).build())
        }

        fun refresh(context: Context, id: Int = AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID && ids(context).isEmpty()) return
            WorkManager.getInstance(context).enqueueUniqueWork("widget-$id", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .setInputData(workDataOf(AppWidgetManager.EXTRA_APPWIDGET_ID to id)).build())
        }
    }
}

class RadarWidgetProvider : RainWidgetProvider()
class ForecastWidgetProvider : RainWidgetProvider()
class ClockWidgetProvider : RainWidgetProvider()
class PlacesWidgetProvider : RainWidgetProvider()
