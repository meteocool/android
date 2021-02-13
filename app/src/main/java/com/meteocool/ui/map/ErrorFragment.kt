package com.meteocool.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.meteocool.R
import com.meteocool.databinding.FragmentErrorBinding
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
    ): View {
        val view : FragmentErrorBinding =  DataBindingUtil.inflate(inflater, R.layout.fragment_error, container, false)
        view.navController = findNavController()
        view.navDirection = R.id.action_retryMap
        return view.root
    }
}
