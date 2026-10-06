package com.meteocool.ui.map

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.meteocool.BuildConfig
import com.meteocool.R
import com.meteocool.databinding.FragmentMapBinding
import com.meteocool.environment.MeteocoolEnvironment
import com.meteocool.location.MeteocoolLocation
import com.meteocool.permissions.PermUtils
import com.meteocool.ui.MeteocoolActivity
import com.meteocool.ui.map.MapViewModel.LocationButtonState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.abs

/**
 * The map: a WebView showing meteocool/core's android.html, with native
 * layer, settings and location buttons floating on top.
 *
 * The page talks back through the `Android` JavaScript interface:
 * requestSettings() when it is ready, and postMessage() with the same
 * messages iOS receives on its scriptHandler, including `share:` from its
 * share buttons.
 */
class WebFragment : Fragment() {

    companion object {
        private const val LOAD_TIMEOUT_MILLIS = 30_000L
        private const val WEB_CACHE_LIMIT_BYTES = 100L * 1024 * 1024
        private val gson = Gson()
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by activityViewModels()

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadTimeout = Runnable { showLoadFailure() }
    private var lastFix: MeteocoolLocation? = null
    private var pendingGeolocation: Pair<String, GeolocationPermissions.Callback>? = null
    private var capabilitiesScript: ScriptHandler? = null
    private var locationAlert: Dialog? = null

    /** Android 14's screenshot callback while the map is resumed; `Any` so older versions never load its class. */
    private var screenCaptureCallback: Any? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                pressLocationButton()
            } else {
                viewModel.prefs.mapZoom = false
                showLocationPermissionAlert()
            }
        }

    private val geolocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val (origin, callback) = pendingGeolocation ?: return@registerForActivityResult
            pendingGeolocation = null
            callback.invoke(origin, grants.values.any { it }, false)
        }

    private val locationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            // Location may have just been turned on; start over so updates flow.
            if (viewModel.buttonState.value != LocationButtonState.OFF) {
                viewModel.locationService.stop()
                viewModel.locationService.start()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()

        binding.layers.setOnClickListener { openLayerSwitcher() }
        binding.settings.setOnClickListener { (activity as? MeteocoolActivity)?.openSettings() }
        binding.locateMe.setOnClickListener { onLocationButtonTapped() }
        binding.retry.setOnClickListener { viewModel.reloadMap() }

        createWebView()

        viewModel.mapUrl.observe(viewLifecycleOwner) { loadMap(it) }
        viewModel.buttonState.observe(viewLifecycleOwner) { renderLocationButton(it) }
        viewModel.fixes.observe(viewLifecycleOwner) { onFix(it) }
        viewModel.webSettingsVersion.observe(viewLifecycleOwner) { injectSettings() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locationService.resolutions.collect { sender ->
                    locationSettingsLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }
        }
    }

    /**
     * The map fills the screen between the system bars; the buttons clear the
     * status bar and cutout.
     *
     * The insets stop here. A WebView that receives them reports them to the
     * page as env(safe-area-inset-*) even though it already sits inside this
     * padding, and the page would clear the bars a second time: the Live pill
     * would sit a status bar's height below the top, and the toolbar as far
     * above the bottom.
     */
    private fun applyInsets() {
        val margin = resources.getDimensionPixelSize(R.dimen.map_control_margin)
        ViewCompat.setOnApplyWindowInsetsListener(binding.mapRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.webContainer.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            binding.controls.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = bars.top + margin
                marginEnd = bars.right + margin
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        val web = WebView(requireContext())
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
        }
        web.webViewClient = MapWebViewClient()
        web.webChromeClient = MapChromeClient()
        web.addJavascriptInterface(Bridge(), "Android")
        web.setOnTouchListener(MapGestureListener())
        binding.webContainer.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        webView = web
        capabilitiesScript = null
    }

    /**
     * Tells the page what the app can do for it before its scripts run
     * (`window.nativeCapabilities`): the share sheet, which makes it show its
     * share buttons. Only for the origin of the page being loaded. A WebView
     * without document-start scripts gets no buttons, as with an app from
     * before sharing; the screenshot offer still works there.
     */
    private fun declareCapabilities(web: WebView, url: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val page = url.toUri()
        val origin = "${page.scheme}://${page.host}" + if (page.port == -1) "" else ":${page.port}"
        capabilitiesScript?.remove()
        capabilitiesScript = WebViewCompat.addDocumentStartJavaScript(web, MapShare.CAPABILITIES_SCRIPT, setOf(origin))
    }

    private fun loadMap(url: String) {
        val web = webView ?: return
        viewModel.pageReady = false
        binding.layers.isEnabled = false
        binding.loadError.isVisible = false
        binding.webContainer.visibility = View.VISIBLE
        mainHandler.removeCallbacks(loadTimeout)
        mainHandler.postDelayed(loadTimeout, LOAD_TIMEOUT_MILLIS)
        web.stopLoading()
        clearCovers()
        declareCapabilities(web, url)
        web.loadUrl(url)
    }

    private fun showLoadFailure() {
        val b = _binding ?: return
        mainHandler.removeCallbacks(loadTimeout)
        viewModel.pageReady = false
        b.layers.isEnabled = false
        // WebView's own error page would show through behind the message.
        b.webContainer.visibility = View.INVISIBLE
        b.loadError.isVisible = true
        clearCovers()
    }

    /** The page called requestSettings(): its window functions exist now. */
    private fun onPageReady() {
        val b = _binding ?: return
        mainHandler.removeCallbacks(loadTimeout)
        viewModel.pageReady = true
        b.loadError.isVisible = false
        b.layers.isEnabled = true
        injectSettings()
        activateLocationIfAuthorized()
        // Not clearCovers(): a deep-linked storm can open its sheet first.
        refreshControls()
    }

    private fun handleMessage(message: String) {
        if (message.startsWith(MapShare.MESSAGE_PREFIX)) {
            MapShare.parse(message.removePrefix(MapShare.MESSAGE_PREFIX), mapHost())?.let { presentShare(it) }
            return
        }
        when (message) {
            "requestSettings" -> onPageReady()
            "layerSwitcherOpened" -> setCovered(Cover.LAYER_SWITCHER, true)
            "layerSwitcherClosed" -> setCovered(Cover.LAYER_SWITCHER, false)
            "detailSheetExpanded" -> setCovered(Cover.EXPANDED_SHEET, true)
            "detailSheetCollapsed" -> setCovered(Cover.EXPANDED_SHEET, false)
            "drawerOpened" -> setCovered(Cover.DRAWER, true)
            "drawerClosed" -> setCovered(Cover.DRAWER, false)
            "impactLight" -> webView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            "impactMedium" -> webView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            "impactHeavy" -> webView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            else -> Timber.d("Unknown message from the page: $message")
        }
    }

    /**
     * What the page has drawn over the buttons' corner. The buttons stay
     * hidden while any of these is open, so closing the layer switcher over
     * an open drawer does not bring them back on top of it. drawerOpened
     * covers every sheet and panel; frontends older than it only send the
     * other two.
     */
    private enum class Cover { LAYER_SWITCHER, EXPANDED_SHEET, DRAWER }

    private val covers = mutableSetOf<Cover>()

    private fun setCovered(cover: Cover, covered: Boolean) {
        if (covered) covers.add(cover) else covers.remove(cover)
        refreshControls()
    }

    /** A new page, or none: nothing it drew is open any more. */
    private fun clearCovers() {
        covers.clear()
        refreshControls()
    }

    private fun refreshControls() {
        _binding?.controls?.isVisible = covers.isEmpty()
    }

    private fun evaluate(script: String, callback: ((String?) -> Unit)? = null) {
        webView?.evaluateJavascript(script) { callback?.invoke(it) }
    }

    private fun injectSettings() {
        if (!viewModel.pageReady) return
        evaluate("window.settings && window.settings.injectSettings(${gson.toJson(viewModel.webSettings())});")
    }

    /**
     * The buttons are hidden by the page's layerSwitcherOpened message, not
     * here: a frontend that never sends layerSwitcherClosed to Android would
     * otherwise leave them hidden for good.
     */
    private fun openLayerSwitcher() {
        if (!viewModel.pageReady) return
        evaluate("window.openLayerswitcher && window.openLayerswitcher();")
    }

    override fun onStart() {
        super.onStart()
        if (!PermUtils.isLocationPermissionGranted(requireContext()) &&
            viewModel.buttonState.value != LocationButtonState.OFF
        ) {
            // Permission was revoked while the app was away.
            setLocationButton(LocationButtonState.OFF)
            viewModel.prefs.mapZoom = false
        }
        if (viewModel.pageReady) {
            evaluate("window.enterForeground && window.enterForeground();")
            if (viewModel.prefs.mapZoom) {
                viewModel.zoomOnce = true
                viewModel.autoFocusOnce = true
            }
        }
        if (viewModel.buttonState.value != LocationButtonState.OFF) viewModel.locationService.start()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) watchScreenshots(true)
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) watchScreenshots(false)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        viewModel.locationService.stop()
        if (viewModel.pageReady) evaluate("window.leaveForeground && window.leaveForeground();")
        trimWebCacheIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacks(loadTimeout)
        webView?.let {
            binding.webContainer.removeView(it)
            it.destroy()
        }
        webView = null
        _binding = null
    }

    /** Back steps back through the page's own history first. Returns whether it did. */
    fun goBack(): Boolean {
        val web = webView ?: return false
        if (!web.canGoBack()) return false
        web.goBack()
        return true
    }

    /* ---- location ------------------------------------------------------ */

    private fun onFix(fix: MeteocoolLocation) {
        lastFix = fix
        sendFix(fix)
    }

    private fun sendFix(fix: MeteocoolLocation) {
        if (!viewModel.pageReady || viewModel.buttonState.value == LocationButtonState.OFF) return
        val focus = viewModel.autoFocus || viewModel.autoFocusOnce
        evaluate(
            "window.lm && window.lm.updateLocation(${fix.latitude}, ${fix.longitude}, " +
                "${fix.accuracy.coerceAtLeast(0f)}, ${viewModel.zoomOnce}, $focus);"
        )
        viewModel.zoomOnce = false
        viewModel.autoFocusOnce = false
    }

    private fun onLocationButtonTapped() {
        if (viewModel.buttonState.value == LocationButtonState.OFF &&
            !PermUtils.isLocationPermissionGranted(requireContext())
        ) {
            locationPermissionLauncher.launch(PermUtils.LOCATION_PERMISSIONS)
            return
        }
        pressLocationButton()
    }

    private fun pressLocationButton() {
        when (viewModel.buttonState.value) {
            LocationButtonState.OFF, null -> {
                viewModel.autoFocusOnce = true
                setLocationButton(LocationButtonState.ACTIVE)
                viewModel.locationService.start()
            }
            LocationButtonState.ACTIVE -> {
                viewModel.autoFocus = true
                viewModel.zoomOnce = true
                setLocationButton(LocationButtonState.TRACKING)
                lastFix?.let { sendFix(it) }
            }
            LocationButtonState.TRACKING -> {
                viewModel.autoFocus = false
                viewModel.autoFocusOnce = false
                viewModel.zoomOnce = false
                setLocationButton(LocationButtonState.OFF)
                viewModel.locationService.stop()
                if (viewModel.pageReady) evaluate("window.lm && window.lm.updateLocation(-1, -1, -1, false, false);")
            }
        }
    }

    /** Turns the location button on once the page is up, if permission is already there. */
    private fun activateLocationIfAuthorized() {
        if (!viewModel.pageReady || !viewModel.prefs.onboardingDone) return
        if (viewModel.buttonState.value != LocationButtonState.OFF) {
            viewModel.locationService.start()
            return
        }
        if (!PermUtils.isLocationPermissionGranted(requireContext())) return
        if (viewModel.prefs.mapZoom) viewModel.zoomOnce = true
        pressLocationButton()
    }

    /** Dragging the map while tracking stops the following, without re-centring. */
    private fun onMapGesture() {
        if (viewModel.buttonState.value != LocationButtonState.TRACKING) return
        viewModel.autoFocus = false
        viewModel.autoFocusOnce = false
        viewModel.zoomOnce = false
        setLocationButton(LocationButtonState.ACTIVE)
    }

    private fun setLocationButton(state: LocationButtonState) {
        viewModel.setButtonState(state)
    }

    private fun renderLocationButton(state: LocationButtonState) {
        binding.locateMe.setImageResource(
            when (state) {
                LocationButtonState.OFF -> R.drawable.ic_location_off
                LocationButtonState.ACTIVE -> R.drawable.ic_location_active
                LocationButtonState.TRACKING -> R.drawable.ic_location_tracking
            }
        )
        ViewCompat.setStateDescription(
            binding.locateMe,
            getString(
                when (state) {
                    LocationButtonState.OFF -> R.string.location_button_off
                    LocationButtonState.ACTIVE -> R.string.location_button_active
                    LocationButtonState.TRACKING -> R.string.location_button_tracking
                }
            )
        )
    }

    private fun showLocationPermissionAlert() {
        locationAlert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_permission_required)
            .setMessage(R.string.location_permission_general)
            .setPositiveButton(R.string.change_in_settings) { _, _ ->
                startActivity(PermUtils.appSettingsIntent(requireContext()))
            }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    /* ---- sharing ------------------------------------------------------- */

    /**
     * The system share sheet for a link to the map, from one of the page's
     * share buttons or from a screenshot (`screenshotTaken`).
     */
    private fun presentShare(share: MapShare) {
        if (!isResumed) return
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, share.url)
            .putExtra(Intent.EXTRA_TITLE, share.title)
            .putExtra(Intent.EXTRA_SUBJECT, share.title)
        try {
            startActivity(Intent.createChooser(send, null))
        } catch (e: ActivityNotFoundException) {
            Timber.w("No share sheet")
        }
    }

    /**
     * Android 14 and later only. Older versions would need a MediaStore
     * observer, which needs permission to read the reader's images.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun watchScreenshots(watch: Boolean) {
        val activity = requireActivity()
        (screenCaptureCallback as? Activity.ScreenCaptureCallback)?.let { activity.unregisterScreenCaptureCallback(it) }
        screenCaptureCallback = null
        if (!watch) return
        val callback = Activity.ScreenCaptureCallback { screenshotTaken() }
        activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
        screenCaptureCallback = callback
    }

    /**
     * The reader just took a screenshot of the map: most likely to send it
     * to someone. Offer the share sheet with a link to the same view, so
     * whoever gets the picture can open the map where it was. The system's
     * screenshot preview already shares the picture itself.
     *
     * Only over the map, and only once the page can say what it shows: a
     * page from before sharing has no `window.shareLink`.
     */
    private fun screenshotTaken() {
        if (!viewModel.pageReady || !isResumed || mapCovered()) return
        evaluate(MapShare.SHARE_LINK_SCRIPT) { result ->
            MapShare.fromScriptResult(result, mapHost())?.let { presentShare(it) }
        }
    }

    /**
     * Whether the app has something over the map: the location alert, or
     * the activity's settings drawer or demo notice. Other screens pause the
     * fragment, which stops the screenshot watch. Window focus cannot tell:
     * the screenshot preview takes it as the screenshot is taken.
     */
    private fun mapCovered(): Boolean =
        locationAlert?.isShowing == true || (activity as? MeteocoolActivity)?.coversMap == true

    /** The host links from the page must be on: the map's own. */
    private fun mapHost(): String? =
        MeteocoolEnvironment.testMapOverride?.toUri()?.host ?: MeteocoolEnvironment.current.webHostName

    /* ---- web cache ----------------------------------------------------- */

    /** WebView's HTTP cache has no size limit of its own; drop it past 100 MB. Local storage stays. */
    private fun trimWebCacheIfNeeded() {
        val context = context?.applicationContext ?: return
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) {
                listOf("WebView", "org.chromium.android_webview")
                    .map { File(context.cacheDir, it) }
                    .sumOf { dir -> dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
            }
            if (size > WEB_CACHE_LIMIT_BYTES) {
                Timber.i("Web cache is ${size / (1024 * 1024)} MB, clearing it")
                webView?.clearCache(true)
            }
        }
    }

    /* ---- WebView plumbing --------------------------------------------- */

    private fun isMapOrigin(uri: Uri?): Boolean {
        if (uri == null) return false
        MeteocoolEnvironment.testMapOverride?.toUri()?.let { test ->
            return uri.scheme == test.scheme && uri.host == test.host && uri.port == test.port
        }
        return uri.scheme == "https" && uri.host == MeteocoolEnvironment.current.webHostName
    }

    /** JavaScript calls arrive on a background thread, from whatever page is loaded. */
    private inner class Bridge {
        @JavascriptInterface
        fun requestSettings() = onMain("requestSettings")

        @JavascriptInterface
        fun postMessage(message: String) = onMain(message)

        private fun onMain(message: String) {
            mainHandler.post {
                val web = webView ?: return@post
                if (!isMapOrigin(web.url?.toUri())) return@post
                handleMessage(message)
            }
        }
    }

    private inner class MapWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (isMapOrigin(request.url)) return false
            try {
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
            } catch (e: ActivityNotFoundException) {
                Timber.w("No app for ${request.url.scheme}")
            }
            return true
        }

        /**
         * Every new page, including the ones the page starts itself: the
         * service worker reloads it when a new frontend is deployed, and a
         * drawer open on the old page never says it closed.
         */
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (view == webView) clearCovers()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                Timber.w("Map failed to load: ${error.errorCode}")
                showLoadFailure()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Timber.w("WebView renderer gone, crashed: ${detail.didCrash()}")
            val b = _binding
            if (view == webView && b != null) {
                b.webContainer.removeView(view)
                view.destroy()
                webView = null
                createWebView()
                showLoadFailure()
            }
            return true
        }
    }

    private inner class MapChromeClient : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (BuildConfig.DEBUG) Timber.tag("WebConsole").d("${message.messageLevel()}: ${message.message()}")
            return BuildConfig.DEBUG
        }

        /**
         * The page's own navigator.geolocation. Answered from the app's
         * permission; WebView never asks the user itself.
         */
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (!isMapOrigin(origin.toUri())) {
                callback.invoke(origin, false, false)
                return
            }
            if (PermUtils.isLocationPermissionGranted(requireContext())) {
                callback.invoke(origin, true, false)
            } else {
                pendingGeolocation = origin to callback
                geolocationPermissionLauncher.launch(PermUtils.LOCATION_PERMISSIONS)
            }
        }
    }

    /** Pans, pinches and rotations, recognised as they begin. The page still gets every event. */
    private inner class MapGestureListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var reported = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    reported = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> report()
                MotionEvent.ACTION_MOVE ->
                    if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) report()
            }
            return false
        }

        private fun report() {
            if (reported) return
            reported = true
            onMapGesture()
        }
    }
}
