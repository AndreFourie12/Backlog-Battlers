package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.core.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// splash screen fragment displaying app branding and routing user to login
class SplashFragment : Fragment() {

    //------------------------------
    // inflates the fragment_splash layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    //------------------------------
    // checks authentication state and navigates to loginFragment or homeFragment
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            delay(1200L)
            val currentUser = ServiceLocator.authRepository.getCurrentUser()
            if (currentUser != null) {
                findNavController().navigate(R.id.action_splashFragment_to_homeFragment)
            } else {
                findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
            }
        }
    }
}
