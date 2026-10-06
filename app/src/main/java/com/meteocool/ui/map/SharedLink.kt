package com.meteocool.ui.map

import com.google.gson.Gson
import com.meteocool.environment.MeteocoolEnvironment
import java.net.URI
import java.net.URISyntaxException

/**
 * Shared map links, made by core's lib/shareLink.ts: the site's root on one of
 * the map's hosts, with what was on screen in the query. App Links bring them
 * here instead of the browser (the intent filter in AndroidManifest.xml).
 */
object SharedLink {

    private val gson = Gson()

    /**
     * The search a link opens in the map, as `?…`, or null for a URL that is
     * not a shared map link: another host, another page (android.html), or the
     * bare root, which is the website rather than a place on the map.
     */
    fun search(link: String?): String? {
        val uri = try {
            URI(link ?: return null)
        } catch (e: URISyntaxException) {
            return null
        }
        if (uri.scheme != "https" || uri.port != -1) return null
        val host = uri.host?.lowercase() ?: return null
        if (MeteocoolEnvironment.entries.none { it.webHostName == host }) return null
        if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") return null
        val query = uri.rawQuery?.takeIf { it.isNotEmpty() } ?: return null
        return "?$query"
    }

    /**
     * JavaScript that opens [search] in the map without reloading it, as the
     * iOS app's MapLink.openScript: through `window.openLink` where core has
     * it, and otherwise the way the back button does, which core's urlState
     * already follows.
     */
    fun openScript(search: String): String = """
        (function (search) {
          if (typeof window.openLink === "function") { window.openLink(search); return; }
          window.history.pushState(window.history.state, "", search);
          window.dispatchEvent(new PopStateEvent("popstate", { state: window.history.state }));
        })(${gson.toJson(search)});
    """.trimIndent()
}
