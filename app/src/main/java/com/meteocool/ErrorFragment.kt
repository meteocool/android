package com.meteocool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import org.jetbrains.anko.sdk25.coroutines.onClick

class ErrorFragment : Fragment(){

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view =  inflater.inflate(R.layout.fragment_error, container, false)

        val retryMap = view.findViewById<Button>(R.id.retry_map)
        retryMap.onClick {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return view
    }
}
