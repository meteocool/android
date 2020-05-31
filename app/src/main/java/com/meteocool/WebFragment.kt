package com.meteocool

import android.content.Context
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


class WebFragment() : Fragment(){

    interface WebViewClientListener{
        fun receivedWebViewError()
    }

    private lateinit var listener : WebViewClientListener
    private lateinit var mWebView : WebView
    private var errorReceived : Boolean = false



    private val webViewModel : WebViewModel by activityViewModels{
        InjectorUtils.provideWebViewModelFactory(requireContext(), requireActivity().application)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mWebView = view.findViewById(R.id.webView)

        val urlObserver = androidx.lifecycle.Observer<String>{
            newUrl ->
            mWebView.stopLoading()
            mWebView.loadUrl(newUrl)
        }



        webViewModel.url.observe(viewLifecycleOwner, urlObserver)

        val webSettings = mWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.setGeolocationEnabled(true)


//        mWebView.loadUrl(viewModel.url.value + locale)
        // Force links and redirects to open in the WebView instead of in a browser
        mWebView.webViewClient = MyWebViewClient(listener)
        return view
    }


    override fun onStop() {
        super.onStop()
        defaultSharedPreferences.edit().putString("map_url", mWebView.url).apply()
    }


    override fun onResume() {
        super.onResume()

        if(Validator.isLocationPermissionGranted(requireActivity().applicationContext)) {
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
            throw ClassCastException((context.toString() +
                " must implement WebViewClientListener"))
        }
    }

    inner class MyWebViewClient(private val listener : WebViewClientListener) : WebViewClient(){

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.d("WebFragment", "onReceivedError ${error!!.description}")
            Log.d("WebFragment", "onReceivedError ${request!!.url}")
            if(request.url.toString() == WebViewModel.MAP_URL){
                errorReceived = true
                listener.receivedWebViewError()
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d("WebFragment", "onPageFinished")
            if(view != null && !errorReceived) {
                view.addJavascriptInterface(WebAppInterface(requireActivity()), "Android")
            }else{
                errorReceived = false
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
    }
}
