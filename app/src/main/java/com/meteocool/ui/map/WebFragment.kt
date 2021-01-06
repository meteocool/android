package com.meteocool.ui.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.gson.Gson
import com.meteocool.R
import com.meteocool.security.Validator
import com.meteocool.injection.InjectorUtils
import com.meteocool.location.*
import com.meteocool.location.service.LocationService
import com.meteocool.location.service.LocationServiceFactory
import com.meteocool.location.service.ServiceType
import com.meteocool.network.NetworkUtils
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.view.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import timber.log.Timber

/**
 * Loads the webapp "meteocool".
 */
class WebFragment() : Fragment() {

    interface WebViewClientListener {
        fun receivedWebViewError()
    }

    private lateinit var listener: WebViewClientListener
    private lateinit var mWebView: WebView

    private lateinit var locationObserver: Observer<MeteocoolLocation?>
    private lateinit var requestSettingsObserver: VoidEventObserver<VoidEvent>

    private lateinit var foregroundLocationService: LocationService

    private var isRequestSettingsCalled : Boolean = false

    private val webViewModel: WebViewModel by activityViewModels {
        InjectorUtils.provideWebViewModelFactory(requireContext(), requireActivity().application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationObserver = Observer {
            Timber.d("Called")
            if (it != null && isRequestSettingsCalled) {
                Timber.d("Updated location: ${it.latitude}, ${it.longitude}, ${it.accuracy}, ${it.altitude}")
                updateUserLocation(it, false)
            } else {
                // TODO Show hint?
            }
        }

        foregroundLocationService = LocationServiceFactory.getLocationService(requireContext(), ServiceType.FRONT)

        requestSettingsObserver = VoidEventObserver {
            Timber.d("requestSetting")
            val settings: Gson = Gson().newBuilder().create()
            val currentSettings = mapOf<String, Boolean>(
                Pair("darkMode", defaultSharedPreferences.getBoolean("map_mode", false)),
                Pair("mapRotation", defaultSharedPreferences.getBoolean("map_rotate", false))
            )
            Timber.d("Updated ")
            val string = "window.injectSettings(${settings.toJson(currentSettings)});"
            mWebView.post {
                run {
                    mWebView.evaluateJavascript(string) {
                        Timber.d(string)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mWebView = view.findViewById(R.id.webView)

        val urlObserver = androidx.lifecycle.Observer<String> { newUrl ->
            mWebView.stopLoading()
            mWebView.loadUrl(newUrl)
        }
        webViewModel.url.observe(viewLifecycleOwner, urlObserver)

        val webSettings = mWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.setGeolocationEnabled(true)

        // mWebView.loadUrl(viewModel.url.value + locale)
        // Force links and redirects to open in the WebView instead of in a browser
        mWebView.webViewClient = MyWebViewClient(listener)
        return view
    }

    override fun onPause() {
        super.onPause()
        mWebView.removeJavascriptInterface("Android")
        defaultSharedPreferences.edit().putString("map_url", mWebView.url).apply()
        foregroundLocationService.stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        mWebView.addJavascriptInterface(WebAppInterface(), "Android")

        webViewModel.requestingSettings.observe(
            viewLifecycleOwner,
            requestSettingsObserver
        )

        webViewModel.locationData.observe(
            viewLifecycleOwner,
            locationObserver
        ) //TODO replace

        if (Validator.isLocationPermissionGranted(requireContext())) {
            Timber.d("Called manual tile")
            val function = "if (window.manualTileUpdateFn) window.manualTileUpdateFn(true);"
            mWebView.post {
                run {
                    mWebView.evaluateJavascript(function) {}
                }
            }
            zoomOnLastKnownLocation()
            foregroundLocationService.requestLocationUpdates()
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        defaultSharedPreferences.edit().putString("map_url", mWebView.url).apply()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as WebViewClientListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException(
                (context.toString() +
                        " must implement WebViewClientListener")
            )
        }
    }

    private fun updateUserLocation(location: MeteocoolLocation, locateMe: Boolean) {
        val string =
            "window.injectLocation(${location.latitude} , ${location.longitude} , ${location.accuracy} , ${locateMe});"
        mWebView.post {
            run {
                mWebView.evaluateJavascript(string) {}
            }
        }
    }

    private fun zoomOnLastKnownLocation(){
        if(isRequestSettingsCalled) {
            Timber.d("Zoomed")
            val lastLocation =
                SharedPrefUtils.getSavedLocationResult(requireContext().defaultSharedPreferences)
            updateUserLocation(lastLocation, true)
        }
    }

    inner class MyWebViewClient(private val listener: WebViewClientListener) : WebViewClient() {

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Timber.d("onReceivedError ${error!!.description}")
            Timber.d("onReceivedError ${request!!.url}")
            if (request.url.toString() == NetworkUtils.MAP_URL) {
                listener.receivedWebViewError()
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

    inner class WebAppInterface {
        @JavascriptInterface
        fun injectLocation() {
            if (Validator.isLocationPermissionGranted(requireContext())) {
                Timber.d("Injected")
                zoomOnLastKnownLocation()
            } else {
                Validator.checkLocationPermissionFragment(requireContext(), requireActivity())
                Timber.d("Requested")
            }
        }

        @JavascriptInterface
        fun showSettings() {
            requireActivity().runOnUiThread {
                webViewModel.openDrawer()
            }
        }

        @JavascriptInterface
        fun requestSettings() {
            Timber.d("requestSettings injected")
            isRequestSettingsCalled = true
            if (defaultSharedPreferences.getBoolean("map_zoom", false)) {
                zoomOnLastKnownLocation()
            }
        }
    }
}

