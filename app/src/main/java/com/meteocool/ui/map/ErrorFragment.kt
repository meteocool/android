package com.meteocool.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.meteocool.R
import timber.log.Timber

/**
 * Displays no network connection.
 * Replaces the default Android WebView error.
 */
class ErrorFragment : Fragment(){

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view =  inflater.inflate(R.layout.fragment_error, container, false)

        val retryMap = view.findViewById<Button>(R.id.retry_map)
        retryMap.setOnClickListener {
            Timber.d("retry clicked")
            requireActivity().supportFragmentManager.popBackStack()
        }

        return view
    }
}
