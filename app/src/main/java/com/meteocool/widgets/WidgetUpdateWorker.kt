package com.meteocool.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meteocool.R
import com.meteocool.app.app
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.ui.MeteocoolActivity
import com.meteocool.weather.WeatherData
import com.meteocool.weather.RainForecast
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

class WidgetUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val requested = inputData.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val ids = RainWidgetProvider.ids(context).filter { requested == AppWidgetManager.INVALID_APPWIDGET_ID || it == requested }
        for (id in ids) {
            currentCoroutineContext().ensureActive()
            render(context, id)
        }
        return Result.success()
    }

    private suspend fun render(context: Context, id: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(id) ?: return
        val kind = RainWidgetProvider.kind(info.provider.className) ?: return
        val config = WidgetStore.load(context, id)
        val views = RemoteViews(context.packageName, R.layout.widget_rain)
        // Launchers reapply updates to existing views, so XML defaults do not clear an old map.
        views.setViewVisibility(R.id.widget_map, View.GONE)
        views.setImageViewBitmap(R.id.widget_map, null)
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val edit = Intent(context, WidgetConfigActivity::class.java)
            .setData(Uri.parse("meteocool-widget://configure/$id"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        views.setOnClickPendingIntent(R.id.widget_edit, PendingIntent.getActivity(context, id, edit, immutable))
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id,
            Intent(RainWidgetProvider.REFRESH).setComponent(info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), immutable))
        val title = context.getString(when (kind) {
            WidgetKind.RADAR -> R.string.widget_radar
            WidgetKind.FORECAST -> R.string.widget_forecast
            WidgetKind.CLOCK -> R.string.widget_clock
            WidgetKind.PLACES -> R.string.widget_places
        })
        views.setTextViewText(R.id.widget_title, if (MeteocoolEnvironment.current == MeteocoolEnvironment.DEMO)
            "$title · ${context.getString(R.string.mode_demo)}" else title)
        views.removeAllViews(R.id.widget_rows)
        if (config == null) {
            views.setTextViewText(R.id.widget_updated, context.getString(R.string.widget_configure))
            views.setOnClickPendingIntent(R.id.widget_content, PendingIntent.getActivity(context, id, edit, immutable))
            manager.updateAppWidget(id, views)
            return
        }
        views.setTextViewText(R.id.widget_updated, context.getString(R.string.car_loading))
        manager.updateAppWidget(id, views)
        val api = MeteocoolEnvironment.currentApiBase
        val appIntensity = context.app.prefs.intensityDbz
        val palette = context.app.prefs.radarColorMapping
        val baseLayer = context.app.prefs.baseLayer
        val threshold = (config.threshold ?: appIntensity).toDouble()
        val current = if (config.places.any { it.current }) WidgetData.currentLocation(context) else null
        var newest: Long? = null
        val limit = if (kind != WidgetKind.PLACES) 1 else visiblePlaceCount(
            manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280),
            context.resources.configuration.fontScale)
        for ((index, place) in config.places.take(limit).withIndex()) {
            val latitude = place.latitude ?: current?.latitude
            val longitude = place.longitude ?: current?.longitude
            val row = RemoteViews(context.packageName, if (kind == WidgetKind.PLACES) R.layout.widget_place_compact else R.layout.widget_place)
            val name = if (place.current) context.getString(R.string.widget_current_location) else place.name
            row.setTextViewText(R.id.widget_place_name, name)
            val forecast = if (latitude != null && longitude != null) WeatherData.forecast(context, latitude, longitude, threshold) else null
            val now = System.currentTimeMillis() / 1000
            val valid = forecast?.takeIf { it.phase(now) != RainForecast.Phase.Unavailable }
            val status = if (latitude == null || longitude == null) context.getString(R.string.widget_no_location)
                else valid?.let { WidgetGraphics.summary(context, it, now) } ?: context.getString(R.string.widget_unavailable)
            row.setTextViewText(R.id.widget_place_summary, status)
            row.setContentDescription(R.id.widget_place_row, "$name. $status")
            if (valid != null) {
                newest = minOf(newest ?: Long.MAX_VALUE, valid.at((valid.observed - 1).coerceAtLeast(0)))
                val picture = if (kind == WidgetKind.CLOCK) WidgetGraphics.clock(context, valid, config.dialHours)
                    else WidgetGraphics.chart(context, valid, if (kind == WidgetKind.RADAR) WidgetRange.HOUR else config.range, kind == WidgetKind.PLACES)
                if (kind != WidgetKind.RADAR || config.overlay == 2) {
                    row.setImageViewBitmap(R.id.widget_chart, picture)
                    row.setViewVisibility(R.id.widget_chart, View.VISIBLE)
                    if (kind == WidgetKind.CLOCK) {
                        val options = manager.getAppWidgetOptions(id)
                        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) - 24
                        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240) -
                            80 - 56 * context.resources.configuration.fontScale
                        row.setViewLayoutHeight(R.id.widget_chart, minOf(width.toFloat(), height).coerceAtLeast(64f), TypedValue.COMPLEX_UNIT_DIP)
                    }
                }
                if (kind == WidgetKind.RADAR && config.overlay == 0) row.setViewVisibility(R.id.widget_place_summary, View.GONE)
            }
            if (latitude != null && longitude != null) {
                val link = Uri.parse(MeteocoolEnvironment.current.webHost).buildUpon()
                    .appendQueryParameter("latLonZ", "$latitude,$longitude,${config.zoom}").build()
                val open = PendingIntent.getActivity(context, id * 8 + index,
                    Intent(context, MeteocoolActivity::class.java).setAction(Intent.ACTION_VIEW).setData(link), immutable)
                row.setOnClickPendingIntent(R.id.widget_place_row, open)
                if (index == 0 && (kind == WidgetKind.RADAR || (kind == WidgetKind.FORECAST && config.showMap))) {
                    // A map without a fresh observation could show an old storm as the present weather.
                    val options = manager.getAppWidgetOptions(id)
                    val wide = kind == WidgetKind.FORECAST || options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val map = if (valid != null) WidgetData.map(context, latitude, longitude, config.zoom, wide) else null
                    if (map != null) {
                        views.setImageViewBitmap(R.id.widget_map, map)
                        views.setOnClickPendingIntent(R.id.widget_map, open)
                        views.setViewVisibility(R.id.widget_map, View.VISIBLE)
                        views.setContentDescription(R.id.widget_map, context.getString(R.string.widget_map_description, name))
                    } else if (kind == WidgetKind.RADAR) {
                        row.setTextViewText(R.id.widget_place_summary, context.getString(R.string.widget_unavailable))
                        row.setViewVisibility(R.id.widget_place_summary, View.VISIBLE)
                    }
                }
            }
            views.addView(R.id.widget_rows, row)
        }
        views.setTextViewText(R.id.widget_updated, newest?.let {
            context.getString(R.string.widget_as_of, WidgetGraphics.timestamp(context, it))
        } ?: context.getString(R.string.widget_refresh_help))
        currentCoroutineContext().ensureActive()
        if (api != MeteocoolEnvironment.currentApiBase || appIntensity != context.app.prefs.intensityDbz ||
            palette != context.app.prefs.radarColorMapping || baseLayer != context.app.prefs.baseLayer ||
            config != WidgetStore.load(context, id) || manager.getAppWidgetInfo(id) == null) return
        manager.updateAppWidget(id, views)
    }
}
