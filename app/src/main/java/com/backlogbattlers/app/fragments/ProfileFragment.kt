package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.backlogbattlers.app.R

// the profile screen.
// the header, badges, showcase and recent activity sections are all laid out
// in fragment_profile, so there is nothing to wire up yet.
class ProfileFragment : Fragment() {

    // inflates the profile fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }
}
//------------------------------EOF------------------------------\\
