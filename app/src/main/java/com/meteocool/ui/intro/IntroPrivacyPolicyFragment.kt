package com.meteocool.ui.intro

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.meteocool.R
import com.meteocool.databinding.IntroPrivacyPolicyBinding

class IntroPrivacyPolicyFragment : Fragment() {

    companion object {
        fun newInstance(): IntroPrivacyPolicyFragment {
            return IntroPrivacyPolicyFragment()
        }
    }

    private var viewDataBinding: IntroPrivacyPolicyBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewDataBinding =
            DataBindingUtil.inflate(inflater, R.layout.intro_privacy_policy, container, false)
        return viewDataBinding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewDataBinding?.description?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewDataBinding = null
    }
}