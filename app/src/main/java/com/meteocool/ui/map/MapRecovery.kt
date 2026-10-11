package com.meteocool.ui.map

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min

/**
 * Keeps the map page alive, so that no state of the web view needs the user
 * to do anything. The same rules as the iOS app's MapRecovery.
 *
 * The page counts as up once it calls requestSettings() ([loadSucceeded]).
 * Until then, and after any of these, the page is loaded again:
 * - the main frame fails to load, or the page does not report in;
 * - the renderer dies, usually from memory pressure;
 * - a map canvas loses its WebGL context and does not get it back
 *   ([GRAPHICS_WATCH]);
 * - the page no longer answers when the app returns to the foreground.
 *
 * Retries back off from one second to [MAX_DELAY_MS], and happen at once when
 * a network comes up or the app returns to the foreground.
 *
 * While the page runs, the view on screen (`window.shareLink()`, the link the
 * share sheet offers) is noted every [VIEW_INTERVAL_MS]. The first reload
 * after a page that ran opens that view again, so a crash in the 3D map comes
 * back to the same storm rather than the radar. Should the page die again
 * before [STABLE_AFTER_MS], the view may be what kills it, and the next reload
 * is the plain map.
 *
 * No WebView here, only what the fragment hands in, so the rules run as unit
 * tests on virtual time.
 */
class MapRecovery(
    private val scope: CoroutineScope,
    /** Loads the map page again, opening `view` (a link's search) if there is one; the load calls [loadStarted]. */
    private val reload: (view: String?) -> Unit,
    /** The page stopped working, whatever the cause. */
    private val wentDown: () -> Unit,
    /** Shows or hides the "trying again" status. */
    private val showStatus: (Boolean) -> Unit,
    /** The page is still loading. */
    private val isLoading: () -> Boolean,
    /** The map is on screen; a page retried behind it can die again unseen. */
    private val isForeground: () -> Boolean,
    /** Asks the page whether it still runs. A hung page never answers. */
    private val probe: (answer: (Boolean) -> Unit) -> Unit,
    /** Asks the page for the search of a link to its view; null from a page that cannot say. */
    private val currentView: (answer: (String?) -> Unit) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    enum class Failure { NAVIGATION, TIMEOUT, CRASH, GRAPHICS, UNRESPONSIVE }

    companion object {
        /** The page reports in within this time, or once loading stops after it. */
        const val READY_TIMEOUT_MS = 20_000L

        /** Longest wait for a page that is still loading on a slow connection. */
        const val MAX_LOAD_TIME_MS = 45_000L

        /** Longest wait between two retries. */
        const val MAX_DELAY_MS = 15_000L

        /**
         * A page up this long resets the backoff when it fails, so a single
         * crash reloads at once while a page that keeps crashing backs off.
         */
        const val STABLE_AFTER_MS = 60_000L

        /** How often the view on screen is noted. */
        const val VIEW_INTERVAL_MS = 3_000L

        /** How long a page back in the foreground has to answer. */
        const val PROBE_TIMEOUT_MS = 10_000L

        private const val TICK_MS = 5_000L

        /** What [probe] evaluates; written for WebViews too old for `?.`. */
        const val PROBE_SCRIPT = "typeof (window.settings && window.settings.injectSettings) === 'function'"

        /** Milliseconds before retry number [failures]: 1, 2, 4 and 8 seconds, then [MAX_DELAY_MS]. */
        fun delayAfter(failures: Int): Long = min(1_000L shl (failures.coerceIn(1, 5) - 1), MAX_DELAY_MS)

        /**
         * Reports a map canvas whose WebGL context stays lost, as
         * `mapGraphicsLost` on `Android.postMessage`.
         *
         * OpenLayers and MapLibre ask for a lost context back and redraw when
         * it returns, which covers a GPU process restart. A context taken
         * away for good (too many contexts, a GPU fault) leaves a blank or
         * frozen map that only a reload brings back.
         *
         * Only canvases inside `#map`, the map on screen, count: the layer
         * switcher's previews and detached maps can lose theirs harmlessly.
         * A hidden page reports nothing, and a page shown again gets five
         * seconds for its contexts to come back first.
         */
        const val GRAPHICS_WATCH = """(() => {
  const graceMs = 5000;
  const lost = new Set();
  let visibleSince = document.visibilityState === "visible" ? Date.now() : Infinity;
  let timer = null;
  document.addEventListener("visibilitychange", () => {
    visibleSince = document.visibilityState === "visible" ? Date.now() : Infinity;
  });
  const check = () => {
    timer = null;
    if (!lost.size) return;
    const shown = Date.now() - visibleSince >= graceMs;
    if (shown && [...lost].some((canvas) => canvas.isConnected && canvas.closest("#map"))) {
      lost.clear();
      if (window.Android && window.Android.postMessage) window.Android.postMessage("mapGraphicsLost");
      return;
    }
    timer = setTimeout(check, graceMs);
  };
  document.addEventListener("webglcontextlost", (event) => {
    if (!(event.target instanceof HTMLCanvasElement)) return;
    lost.add(event.target);
    if (timer === null) timer = setTimeout(check, graceMs);
  }, true);
  document.addEventListener("webglcontextrestored", (event) => lost.delete(event.target), true);
})();"""
    }

    private var failures = 0

    /** When the page last reported in. Null while loading or failed. */
    private var readySince: Long? = null

    /** Counts the pages that reported in, so an answer is matched to the page asked. */
    private var page = 0

    /** Running while a load is in progress. */
    private var watchdog: Job? = null

    /** Waiting to retry. */
    private var retry: Job? = null
    private var retryInForeground = false

    /** Notes the view while the page runs. */
    private var viewWatch: Job? = null

    /** The search of the link to the view on screen, last time it was asked. */
    private var lastView: String? = null

    /** A load of the map page started, by [reload] or by the page itself. */
    fun loadStarted() {
        retry?.cancel()
        retry = null
        retryInForeground = false
        forgetOldFailures()
        readySince = null
        stopWatchingView()
        watchdog?.cancel()
        // Counts ticks rather than reading the clock, so time the device
        // spends asleep does not count against the page.
        watchdog = scope.launch {
            var waited = 0L
            while (true) {
                delay(TICK_MS)
                waited += TICK_MS
                if (waited >= MAX_LOAD_TIME_MS || (waited >= READY_TIMEOUT_MS && !isLoading())) {
                    failed(Failure.TIMEOUT)
                    return@launch
                }
            }
        }
    }

    /** The page reported in. */
    fun loadSucceeded() {
        watchdog?.cancel()
        watchdog = null
        readySince = now()
        page += 1
        showStatus(false)
        watchView()
    }

    /**
     * The page failed to load, or died after loading. Ignored while already
     * waiting to retry, so two reports of one failure count once.
     */
    fun failed(failure: Failure) {
        if (watchdog == null && readySince == null) return
        watchdog?.cancel()
        watchdog = null
        forgetOldFailures()
        readySince = null
        viewWatch?.cancel()
        viewWatch = null
        failures += 1
        Timber.w("Map failed (${failure.name.lowercase()}), attempt $failures")
        wentDown()
        // The first retry is quick and usually works: say nothing until it fails too.
        if (failures > 1) showStatus(true)
        if (!isForeground()) {
            retryInForeground = true
            return
        }
        val wait = delayAfter(failures)
        retry = scope.launch {
            delay(wait)
            retryNow()
        }
    }

    /**
     * Retries a failed page now instead of after the backoff. A load in
     * progress goes on. Without [restoringView], the page comes back with its
     * own view instead of the one it died with.
     */
    fun hurry(restoringView: Boolean = true) {
        if (!restoringView) lastView = null
        if (retryInForeground || retry != null) retryNow()
    }

    /** A network came up: a page waiting to retry has a reason to work now. */
    fun networkAvailable() {
        if (retry != null) retryNow()
    }

    /**
     * The app returned to the foreground. Retries a failed page at once, and
     * asks a loaded one whether it still runs: a renderer can hang, or die
     * without saying so.
     */
    fun becameActive() {
        hurry()
        if (readySince == null) return
        val asked = page
        var answered = false
        val timeout = scope.launch {
            delay(PROBE_TIMEOUT_MS)
            if (!answered) unresponsive(asked)
        }
        probe { alive ->
            answered = true
            timeout.cancel()
            if (!alive) unresponsive(asked)
        }
    }

    /** Fails the page that was asked, not one loaded since. */
    private fun unresponsive(asked: Int) {
        if (readySince != null && page == asked) failed(Failure.UNRESPONSIVE)
    }

    private fun retryNow() {
        retry?.cancel()
        retry = null
        retryInForeground = false
        reload(if (failures == 1) lastView else null)
    }

    /** Asks the page for its view every [VIEW_INTERVAL_MS] while it runs. */
    private fun watchView() {
        viewWatch?.cancel()
        viewWatch = scope.launch {
            while (true) {
                currentView { view -> if (view != null) lastView = view }
                delay(VIEW_INTERVAL_MS)
            }
        }
    }

    /** A new page, other than a retry, starts from its own view. */
    private fun stopWatchingView() {
        viewWatch?.cancel()
        viewWatch = null
        lastView = null
    }

    private fun forgetOldFailures() {
        val since = readySince ?: return
        if (now() - since > STABLE_AFTER_MS) failures = 0
    }
}
