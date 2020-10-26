package com.meteocool

import Resource
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.observe
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import com.meteocool.location.LocationModel
import com.meteocool.security.Validator
import com.meteocool.utility.InjectorUtils
import com.meteocool.utility.NetworkUtils
import com.meteocool.view.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import timber.log.Timber


class WebFragment() : Fragment() {

    interface WebViewClientListener {
        fun receivedWebViewError()
    }

    private lateinit var listener: WebViewClientListener
    private lateinit var mWebView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var requestForegroundLocationObserver: EventObserver<Boolean>
    private lateinit var locationObserver: Observer<in Resource<LocationModel>>
    private lateinit var requestSettingsObserver: VoidEventObserver<VoidEvent>

    private val webViewModel: WebViewModel by activityViewModels {
        InjectorUtils.provideWebViewModelFactory(requireContext(), requireActivity().application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                  //  updateUserLocation(location)
                }
            }
        }

        locationObserver = Observer {
            Timber.d("Called")
            if(it.isSuccessful) {
                Timber.d("Updated location: ${it.data().latitude}, ${it.data().longitude}, ${it.data().accuracy}, ${it.data().altitude}")
                updateUserLocation(it.data())
            }else{
                if (it.error() is ResolvableApiException) {
                    (it.error() as ResolvableApiException).startResolutionForResult(
                        requireActivity(),
                        MeteocoolActivity.REQUEST_CHECK_SETTINGS
                    )
                }
            }

        }

        requestForegroundLocationObserver = EventObserver {
            if (it) {
                Timber.d("requestLocation $it")
                requestLocationUpdates()
            }else{
                stopLocationUpdates()
            }
        }

        requestSettingsObserver = VoidEventObserver {
            Timber.d("requestSetting")
            val settings: Gson = Gson().newBuilder().create()
            val currentSettings = mapOf<String, Boolean>(
                Pair("darkMode", defaultSharedPreferences.getBoolean("map_mode", false)),
                //Pair("zoomOnForeground", defaultSharedPreferences.getBoolean("map_zoom", false)),
                Pair("mapRotation", defaultSharedPreferences.getBoolean("map_rotate", false))
            )
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    override fun onPause() {
        super.onPause()
       // mWebView.removeJavascriptInterface("Android")
        defaultSharedPreferences.edit().putString("map_url", mWebView.url).apply()
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
        )

//        webViewModel.requestingLocationUpdatesForeground.observe(
//            viewLifecycleOwner,
//            requestForegroundLocationObserver
//        )


        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Timber.d("Called manual tile")
            val function = "if (window.manualTileUpdateFn) window.manualTileUpdateFn(true);"
            mWebView.post {
                run {
                    mWebView.evaluateJavascript(function) {}
                }
            }
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

    private fun updateUserLocation(location: LocationModel) {
        val string =
            "window.injectLocation(${location.latitude} , ${location.longitude} , ${location.accuracy} , true);"
        mWebView.post {
            run {
                mWebView.evaluateJavascript(string) {}
            }
        }
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        Timber.i("Stop location updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun requestLocationUpdates() {
        try {
            val builder =
                LocationSettingsRequest.Builder()
                    .addLocationRequest(frontendLocationRequest)
            val client: SettingsClient = LocationServices.getSettingsClient(requireActivity())
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
            task.addOnSuccessListener {

                Timber.i("Starting location updates")

                fusedLocationClient.requestLocationUpdates(
                    frontendLocationRequest, locationCallback, Looper.getMainLooper()
                )

            }

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        exception.startResolutionForResult(
                            requireActivity(),
                            MeteocoolActivity.REQUEST_CHECK_SETTINGS
                        )
                        Timber.d("No location permission")
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e)
        }
    }

    private val frontendLocationRequest: LocationRequest
        get() {
            return LocationRequest.create().apply {
                interval = 2
                fastestInterval = 1
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                maxWaitTime = 2
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
            Validator.checkLocationPermission(requireContext(), requireActivity())
            requireActivity().runOnUiThread {

                webViewModel.getLocation()
               //webViewModel.locationData.observe(requireActivity(), locationObserver)
                //webViewModel.sendLocationOnce(Validator.isLocationPermissionGranted(requireContext())  && defaultSharedPreferences.getBoolean("map_zoom", false))
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
        }
    }
}

