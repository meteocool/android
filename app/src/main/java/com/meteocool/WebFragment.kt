package com.meteocool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.activityViewModels
import com.meteocool.location.WebAppInterface
import com.meteocool.security.Validator
import com.meteocool.utility.InjectorUtils
import com.meteocool.view.WebViewModel
import org.jetbrains.anko.support.v4.defaultSharedPreferences


class WebFragment() : Fragment() {

    interface WebViewClientListener {
        fun receivedWebViewError()
    }

    private lateinit var listener: WebViewClientListener
    private lateinit var mWebView: WebView


    private val webViewModel: WebViewModel by activityViewModels {
        InjectorUtils.provideWebViewModelFactory(requireContext(), requireActivity().application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferenceObserver = androidx.lifecycle.Observer<Boolean>{
                isRotationActive ->
            val webAppInterface = WebAppInterface(requireActivity())
            webAppInterface.requestSettings()
            Log.d("Map Rotation", "$isRotationActive")
        }
        webViewModel.isMapRotateActive.observe(this, preferenceObserver)
        webViewModel.isNightModeEnabled.observe(this, preferenceObserver)
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
        mWebView.addJavascriptInterface(WebAppInterface(requireActivity()), "Android")

    }

    override fun onPause() {
        super.onPause()
        mWebView.removeJavascriptInterface("Android")
        defaultSharedPreferences.edit().putString("map_url", mWebView.url).apply()
    }

    override fun onResume() {
        super.onResume()

        if (Validator.isLocationPermissionGranted(requireActivity().applicationContext)) {
            val string = "window.manualTileUpdateFn(true);"
            mWebView.post {
                run {
                    mWebView.evaluateJavascript(string) {}
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

    inner class MyWebViewClient(private val listener: WebViewClientListener) : WebViewClient() {

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.d("WebFragment", "onReceivedError ${error!!.description}")
            Log.d("WebFragment", "onReceivedError ${request!!.url}")
            if (request.url.toString() == WebViewModel.MAP_URL) {
                listener.receivedWebViewError()
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Log.d("WebFragment", "onReceivedHttpError")
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (Uri.parse(url).host!!.contains("www.meteocool.com")) {
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
}
