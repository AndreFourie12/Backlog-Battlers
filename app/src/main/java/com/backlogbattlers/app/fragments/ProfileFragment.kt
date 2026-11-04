package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R

// the profile screen.
// the header, badges, showcase and recent activity sections are all laid out
// in fragment_profile, so all this has to do is wire up its buttons.
class ProfileFragment : Fragment() {

    // inflates the profile fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    // the header's sun opens appearance (theme and backdrop) and its gear opens settings.
    // the empty recent activity card's "add a game" goes to search, the same as home's
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_appearance).setOnClickListener {
            Log.d(TAG, "Opening appearance from the profile header")
            findNavController().navigate(R.id.action_profileFragment_to_appearanceFragment)
        }

        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            Log.d(TAG, "Opening settings from the profile header")
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }

        view.findViewById<View>(R.id.btn_add_game).setOnClickListener {
            Log.d(TAG, "Opening search to add a first game from the profile")
            findNavController().navigate(R.id.action_profileFragment_to_searchFragment)
        }
    }

    private companion object {
        const val TAG = "ProfileFragment"
    }
}
//------------------------------EOF------------------------------\\
