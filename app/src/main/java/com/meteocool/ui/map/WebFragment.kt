package com.meteocool.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.meteocool.R
import com.meteocool.databinding.FragmentMapBinding
import com.meteocool.injection.InjectorUtils
import com.meteocool.location.*
import com.meteocool.network.NetworkUtils
import com.meteocool.permissions.PermUtils
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.view.*
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Loads the webapp "meteocool".
 */
class WebFragment : Fragment() {

    private lateinit var locationObserver: Observer<Resource<MeteocoolLocation>>
    private lateinit var requestSettingsObserver: VoidEventObserver<VoidEvent>

    /**
     * Use databinding for this fragment.
     */
    private lateinit var viewDataBinding: FragmentMapBinding

    //    private lateinit var requestPermissionLauncher : ActivityResultLauncher<String>
    private var isRequestSettingsCalled: Boolean = false
    private var isZoom: Boolean = false
    private var isStartFocus: Boolean = true

    private lateinit var requestLocationPermissionLauncher: ActivityResultLauncher<String>

    private val webViewModel: WebViewModel by activityViewModels {
        InjectorUtils.provideWebViewModelFactory(requireActivity().application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                Timber.d("$isGranted")
                if (isGranted) {
                    locateMe()
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewDataBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_map, container, false)

        viewDataBinding.lifecycleOwner = viewLifecycleOwner
        viewDataBinding.viewmodel = webViewModel
        viewDataBinding.layerFunction = Runnable {
            viewDataBinding.webView.evaluateJavascript("window.openLayerswitcher();") {
            }
        }

        val webSettings = viewDataBinding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.setGeolocationEnabled(true)

        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                webSettings.forceDark = WebSettings.FORCE_DARK_ON
            }
        }

        viewDataBinding.webView.webViewClient = MyWebViewClient()

        return viewDataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewDataBinding.locateMe.setOnClickListener {
            locateMe()
        }

        requestSettingsObserver = VoidEventObserver {
            Timber.d("requestSetting")
            val settings: Gson = Gson().newBuilder().create()
            val currentSettings = mapOf(
                Pair(
                    "mapRotation",
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getBoolean("map_rotate", false)
                ),
            )
            Timber.d("Updated ")
            windowSettingsInjectSettings(settings, currentSettings)
        }

        viewDataBinding.webView.addJavascriptInterface(WebAppInterface(), "Android")

        webViewModel.url.observe(viewLifecycleOwner, { newUrl ->
            viewDataBinding.webView.stopLoading()
            viewDataBinding.webView.loadUrl(
                newUrl + "v=${
                    SharedPrefUtils.getAppVersion(
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                    )
                }"
            )
        })

        locationObserver = Observer {
            Timber.d("Location Live Data")
            if (it.isSuccessful) {
                if (isRequestSettingsCalled) {
                    Timber.d(it.data().toString())
                    updateUserLocation(
                        it.data(),
                        isZoom,
                        isStartFocus || isZoom
                    )
                    isZoom = false
                    isStartFocus = false
                }
            } else {
                if (it.error() != null && it.error() is ResolvableApiException) {
                    (it.error() as ResolvableApiException).startResolutionForResult(
                        requireActivity(),
                        1
                    )
                    Timber.d(it.error())
                }
            }
        }

    }

    private fun windowSettingsInjectSettings(
        settings: Gson,
        currentSettings: Map<String, Boolean>
    ) {
        val string = "window.settings.injectSettings(${settings.toJson(currentSettings)});"
        viewDataBinding.webView.post {
            run {
                viewDataBinding.webView.evaluateJavascript(string) {
                    Timber.d(string)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        windowEnterForeground()

        if (PermUtils.isLocationPermissionGranted(requireContext())) {
            webViewModel.requestForegroundLocationUpdates()
        }
    }

    private fun windowEnterForeground() {
        if (isRequestSettingsCalled) {
            val function = "window.enterForeground();"
            try {
                viewDataBinding.webView.post {
                    run {
                        viewDataBinding.webView.evaluateJavascript(function) {}
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
        }
    }

    private fun unregisterTileUpdates() {
        val function = "window.leaveForeground();"
        viewDataBinding.webView.post {
            run {
                viewDataBinding.webView.evaluateJavascript(function) {}
            }
        }
    }


    override fun onResume() {
        super.onResume()
        Timber.d("onResume")

        if (isRequestSettingsCalled) {
            webViewModel.requestingSettings.observe(
                viewLifecycleOwner,
                requestSettingsObserver
            )
        }

        webViewModel.locationData.observe(viewLifecycleOwner, locationObserver)

        if (PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("map_zoom", false)
        ) {
            zoomOnLastKnownLocation()
        }
    }

    override fun onStop() {
        super.onStop()
        webViewModel.stopForegroundLocationUpdates()
        unregisterTileUpdates()
    }

    private fun updateUserLocation(location: MeteocoolLocation, isZoom: Boolean, isFocus: Boolean) {
        val string =
            "window.lm.updateLocation(${location.latitude}, ${location.longitude}, ${location.accuracy}, ${isZoom}, ${isFocus});"
        Timber.d(string)
        viewDataBinding.webView.post {
            run {
                viewDataBinding.webView.evaluateJavascript(string) {
                }
            }
        }
    }

    private fun zoomOnLastKnownLocation() {
        if (isRequestSettingsCalled) {
            Timber.d("Zoomed")
            val lastLocation =
                SharedPrefUtils.getSavedLocationResult(
                    PreferenceManager.getDefaultSharedPreferences(
                        requireContext()
                    )
                )
            updateUserLocation(lastLocation, true, true)
            isZoom = true
        }
    }

    inner class MyWebViewClient() : WebViewClient() {

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Timber.d("onReceivedError ${error!!.description}")
            Timber.d("onReceivedError ${request!!.url}")
            if (request.url.toString() == NetworkUtils.MAP_URL) {
                viewDataBinding.webView.findNavController().navigate(R.id.event_error)
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Timber.d("onReceivedHttpError")
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (Uri.parse(url).host!!.contains("meteocool.com")) {
                // This is my web site, so do not override; let my WebView load the page
                return false
            }
            // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                startActivity(this)
            }
            return true
        }
    }

    private fun locateMe() {
        Timber.d("locateMe")
        isZoom = true
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                requireActivity().applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                Timber.d("Granted")
                webViewModel.requestForegroundLocationUpdates()
                zoomOnLastKnownLocation()
            }
            else -> {
                requestLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun requestSettings() {
            Timber.d("requestSettings injected")
            isRequestSettingsCalled = true
            webViewModel.sendSettings()
            if (PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean("map_zoom", false)
            ) {
                zoomOnLastKnownLocation()
            }
        }
    }
}

