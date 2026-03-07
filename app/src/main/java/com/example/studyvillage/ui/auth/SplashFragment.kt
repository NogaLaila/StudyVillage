package com.example.studyvillage.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.studyvillage.R
import com.example.studyvillage.util.UserSession

class SplashFragment : Fragment(R.layout.fragment_splash) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.postDelayed({
            if (UserSession.isLoggedIn()) {
                findNavController().navigate(R.id.action_splashFragment_to_focusFragment)
            } else {
                findNavController().navigate(R.id.action_splashFragment_to_welcomeFragment)
            }
        }, 800)
    }
}