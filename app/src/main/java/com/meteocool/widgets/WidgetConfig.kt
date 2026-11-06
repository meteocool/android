package com.meteocool.widgets

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.meteocool.preferences.Prefs

enum class WidgetKind { RADAR, FORECAST, CLOCK, PLACES }
enum class WidgetRange(val past: Int, val ahead: Int) {
    @SerializedName("HOUR") HOUR(0, 12),
    @SerializedName("TWO_HOURS") TWO_HOURS(0, 24),
    @SerializedName("AROUND_NOW") AROUND_NOW(6, 18),
    @SerializedName("ALL") ALL(24, 24),
}

data class WidgetPlace(
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
) {
    val current: Boolean get() = latitude == null && longitude == null
    fun valid(): Boolean = name.isNotBlank() && name.length <= 200 &&
        (current || (latitude != null && longitude != null && validCoordinate(latitude, longitude)))
}

data class WidgetConfig(
    @SerializedName("places") val places: List<WidgetPlace> = listOf(WidgetPlace("current")),
    @SerializedName("range") val range: WidgetRange = WidgetRange.TWO_HOURS,
    @SerializedName("zoom") val zoom: Double = 9.5,
    @SerializedName("overlay") val overlay: Int = 1,
    @SerializedName("threshold") val threshold: Int? = null,
    @SerializedName("showMap") val showMap: Boolean = true,
    @SerializedName("dialHours") val dialHours: Int = 1,
) {
    companion object {
        val zooms = listOf(11.0, 9.5, 8.0, 6.5)
        private val gson = Gson()
        fun encode(config: WidgetConfig): String = gson.toJson(config)

        /** Settings are private, but corrupted/restored data must not become an API coordinate. */
        fun decode(value: String?): WidgetConfig? = try {
            val json = JsonParser.parseString(value ?: "").asJsonObject
            val places = json.getAsJsonArray("places").map { element ->
                val place = element.asJsonObject
                WidgetPlace(place["name"].asString,
                    place["latitude"]?.takeUnless { it.isJsonNull }?.asDouble,
                    place["longitude"]?.takeUnless { it.isJsonNull }?.asDouble)
            }
            WidgetConfig(places, WidgetRange.valueOf(json["range"].asString), json["zoom"].asDouble,
                json["overlay"].asInt, json["threshold"]?.takeUnless { it.isJsonNull }?.asInt,
                json["showMap"].asBoolean, json["dialHours"].asInt).takeIf {
                it.places.size in 1..6 && it.places.all(WidgetPlace::valid) && it.zoom in zooms &&
                    it.overlay in 0..2 && (it.threshold == null || it.threshold in Prefs.INTENSITIES) &&
                    it.dialHours in 1..2
            }
        } catch (_: RuntimeException) { null }
    }
}

fun validCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

fun visiblePlaceCount(heightDp: Int, fontScale: Float): Int =
    ((heightDp - 76 - 30 * fontScale) / maxOf(56f, 48 * fontScale + 8)).toInt().coerceIn(1, 6)

object WidgetStore {
    const val PREFERENCES = "widgets"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    fun load(context: Context, id: Int): WidgetConfig? = WidgetConfig.decode(prefs(context).getString(id.toString(), null))
    fun save(context: Context, id: Int, config: WidgetConfig) {
        prefs(context).edit { putString(id.toString(), WidgetConfig.encode(config)) }
    }
    fun delete(context: Context, id: Int) { prefs(context).edit { remove(id.toString()) } }
}
