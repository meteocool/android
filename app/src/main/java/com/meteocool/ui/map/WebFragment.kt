package com.meteocool.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.gson.Gson
import com.meteocool.R
import com.meteocool.databinding.FragmentMapBinding
import com.meteocool.injection.InjectorUtils
import com.meteocool.location.*
import com.meteocool.network.NetworkUtils
import com.meteocool.permissions.PermUtils
import com.meteocool.preferences.SharedPrefUtils
import com.meteocool.view.*
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.annotations.AfterPermissionGranted
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.support.v4.defaultSharedPreferences
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
    private var wasButtonLocateMePressed: Boolean = false

    private val webViewModel: WebViewModel by activityViewModels {
        InjectorUtils.provideWebViewModelFactory(requireActivity().application)
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

//        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
//        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
//                webSettings.forceDark = WebSettings.FORCE_DARK_ON
//            }
//        }

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
                Pair("mapRotation", defaultSharedPreferences.getBoolean("map_rotate", false)),
            )
            Timber.d("Updated ")
            val string = "window.settings.injectSettings(${settings.toJson(currentSettings)});"
            viewDataBinding.webView.post {
                run {
                    viewDataBinding.webView.evaluateJavascript(string) {
                        Timber.d(string)
                    }
                }
            }
        }

        webViewModel.url.observe(viewLifecycleOwner, { newUrl ->
            viewDataBinding.webView.stopLoading()
            viewDataBinding.webView.loadUrl(newUrl)
        })

        locationObserver = Observer {
            Timber.d("Location Live Data")
            if (it.isSuccessful) {
                if (isRequestSettingsCalled) {
                    Timber.d(it.data().toString())
                    updateUserLocation(it.data(), false, wasButtonLocateMePressed)
                    wasButtonLocateMePressed = false
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

    override fun onStart() {
        super.onStart()
        viewDataBinding.webView.addJavascriptInterface(WebAppInterface(), "Android")

        if (isRequestSettingsCalled) {
            registerTileUpdates()
        }

        if (EasyPermissions.hasPermissions(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            webViewModel.requestForegroundLocationUpdates()
        }
    }

    private fun registerTileUpdates() {
        val function = "window.enterForeground();"
        viewDataBinding.webView.post {
            run {
                viewDataBinding.webView.evaluateJavascript(function) {}
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

        webViewModel.requestingSettings.observe(
            viewLifecycleOwner,
            requestSettingsObserver
        )

        webViewModel.locationData.observe(viewLifecycleOwner, locationObserver)

        if (defaultSharedPreferences.getBoolean("map_zoom", false)) {
            zoomOnLastKnownLocation()
        }
    }

    override fun onStop() {
        super.onStop()
        viewDataBinding.webView.removeJavascriptInterface("Android")
        webViewModel.stopForegroundLocationUpdates()
        unregisterTileUpdates()
    }

    private fun updateUserLocation(location: MeteocoolLocation, isZoom: Boolean, isFocus: Boolean) {
        val string =
            "window.lm.updateLocation(${location.latitude}, ${location.longitude}, ${location.accuracy}, ${isZoom}, ${isFocus});"
        viewDataBinding.webView.post {
            run {
                viewDataBinding.webView.evaluateJavascript(string) {}
            }
        }
    }

    private fun zoomOnLastKnownLocation() {
        if (isRequestSettingsCalled) {
            Timber.d("Zoomed")
            val lastLocation =
                SharedPrefUtils.getSavedLocationResult(requireContext().defaultSharedPreferences)
            updateUserLocation(lastLocation, true, true)
            wasButtonLocateMePressed = true
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

    @AfterPermissionGranted(PermUtils.LOCATION)
    private fun locateMe() {
        Timber.d("locateMe")
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                requireActivity().applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                Timber.d("Granted")
                //TODO? Fix the zoom on cached location if location is turned off
                webViewModel.requestForegroundLocationUpdates()
                zoomOnLastKnownLocation()
            }
            else -> {
                EasyPermissions.requestPermissions(
                    host = this,
                    rationale = getString(R.string.dialog_msg_locate_me),
                    requestCode = PermUtils.LOCATION,
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
            registerTileUpdates()
            if (defaultSharedPreferences.getBoolean("map_zoom", false)) {
                zoomOnLastKnownLocation()
            }
        }
    }
}

