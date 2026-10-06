package com.meteocool.ui.map

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.net.URI
import java.net.URISyntaxException

/**
 * A link to what the map shows, from core's lib/share.ts: the body of a
 * `share:` message from one of the page's share buttons, or the answer to
 * `window.shareLink()` when the app starts the share itself (a screenshot).
 *
 * The page builds the link, because only the page knows what is on screen:
 * the map, the view, the storm, the frame. The link carries when it was
 * shared (`shared=`), so whoever opens it later is told how old it is.
 *
 * No Android dependencies, so the checks run as plain unit tests.
 */
data class MapShare(val url: String, val title: String) {

    companion object {
        /** The page's message: `share:` and the JSON of core's `NativeShare`. */
        const val MESSAGE_PREFIX = "share:"

        /**
         * The longest title passed on. The page's are a place name and the
         * app's name; anything longer is not one of those.
         */
        const val MAX_TITLE_LENGTH = 200

        /**
         * Tells the page, before its scripts run, that the app has a share
         * sheet for it. Only then does it show its share buttons in an app.
         */
        const val CAPABILITIES_SCRIPT =
            "window.nativeCapabilities = Object.assign(window.nativeCapabilities || {}, { share: true });"

        /**
         * Asks the page for a link to what it shows. A page from before
         * sharing has no `window.shareLink`, and it answers null until the
         * map is up.
         */
        const val SHARE_LINK_SCRIPT = "JSON.stringify(window.shareLink ? window.shareLink() : null)"

        private val LOOPBACK = setOf("127.0.0.1", "localhost")

        /**
         * Reads a share from the page's JSON. Null for anything that is not a
         * link back to the map's host ([mapHost]), so the share sheet can only
         * ever offer a meteocool link: the page is the app's own, but its
         * strings are still checked before use. The button's rect (`x`, `y`,
         * `width`, `height`), which an iPad's popover points at, has no use
         * here.
         */
        fun parse(json: String, mapHost: String?): MapShare? {
            val share = parseJson(json)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val url = share.string("url")?.takeIf { isMapLink(it, mapHost) } ?: return null
            val title = share.string("title")?.trim().orEmpty()
            return MapShare(url, if (title.isEmpty()) "meteocool" else title.limit(MAX_TITLE_LENGTH))
        }

        /**
         * Reads the answer to [SHARE_LINK_SCRIPT]. evaluateJavascript hands
         * back its result as JSON, and the result is JSON text itself.
         */
        fun fromScriptResult(result: String?, mapHost: String?): MapShare? {
            val text = result?.let(::parseJson)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            return text?.let { parse(it.asString, mapHost) }
        }

        private fun isMapLink(url: String, mapHost: String?): Boolean {
            val uri = try {
                URI(url)
            } catch (e: URISyntaxException) {
                return false
            }
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            if (mapHost == null || host != mapHost.lowercase()) return false
            // Plain http only for a map served from the loopback, which only test builds load.
            return scheme == "https" || (scheme == "http" && host in LOOPBACK)
        }

        private fun parseJson(text: String): JsonElement? = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            null
        }

        private fun JsonObject.string(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

        /** At most [max] characters, without splitting one that takes two chars. */
        private fun String.limit(max: Int): String =
            if (codePointCount(0, length) <= max) this else substring(0, offsetByCodePoints(0, max))
    }
}
