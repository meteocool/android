package com.meteocool.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import timber.log.Timber

data class ReceivedAlert(
    @SerializedName("request") val request: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("received") val received: Long,
)

object AlertHistory {
    private val gson = Gson()
    private const val KEPT = 14L * 24 * 60 * 60 * 1000
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "alerts.json"))

    fun merged(existing: List<ReceivedAlert>, new: List<ReceivedAlert>, now: Long): List<ReceivedAlert> {
        val result = existing.toMutableList()
        new.forEach { alert ->
            if (result.none { it.request == alert.request ||
                (kotlin.math.abs(it.received - alert.received) < 120_000 && it.title == alert.title && it.body == alert.body) }) result.add(alert)
        }
        return result.filter { now - it.received in 0 until KEPT }.sortedByDescending { it.received }.take(200)
    }

    @Synchronized fun load(context: Context): List<ReceivedAlert> = try {
        file(context).openRead().bufferedReader().use {
            val alerts = gson.fromJson(it, Array<ReceivedAlert>::class.java)?.toList() ?: emptyList()
            merged(alerts, emptyList(), System.currentTimeMillis())
        }
    } catch (_: java.io.FileNotFoundException) { emptyList() }
      catch (e: Exception) { Timber.w("Alert history: ${e.javaClass.simpleName}"); emptyList() }

    @Synchronized fun add(context: Context, alert: ReceivedAlert) {
        val target = file(context)
        var stream: java.io.FileOutputStream? = null
        try {
            val bytes = gson.toJson(merged(load(context), listOf(alert), System.currentTimeMillis())).toByteArray()
            stream = target.startWrite()
            stream.write(bytes)
            target.finishWrite(stream)
        } catch (e: Exception) {
            target.failWrite(stream)
            Timber.w("Alert history save: ${e.javaClass.simpleName}")
        }
    }

    /** FCM auto-displays background notifications without invoking the app's service. */
    fun captureDelivered(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.forEach { item ->
            val extra = item.notification.extras
            if (item.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return@forEach
            val title = extra.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return@forEach
            val body = (extra.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extra.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return@forEach
            val rain = item.notification.channelId == context.getString(com.meteocool.R.string.notification_channel_id)
            if (rain) add(context, ReceivedAlert(item.key, title, body, item.postTime))
        }
    }

    @Synchronized fun clear(context: Context) = file(context).delete()
}
