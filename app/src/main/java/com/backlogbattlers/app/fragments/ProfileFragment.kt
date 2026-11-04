package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.bindProfileAvatar
import com.backlogbattlers.app.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

// the profile screen.
// the header, badges, showcase and recent activity sections are all laid out
// in fragment_profile, so all this has to do is wire up its buttons and show
// the signed-in user's name and picture in the header.
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

    // inflates the profile fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    // the header's pencil opens edit profile (picture, name, what friends see) and its gear opens
    // settings. the empty recent activity card's "add a game" goes to search, the same as home's
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_edit_profile).setOnClickListener {
            Log.d(TAG, "Opening edit profile from the profile header")
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        // until someone is signed in the header keeps the sample name the layout was drawn with
        val avatar = view.findViewById<View>(R.id.avatarProfile)
        val name = view.findViewById<TextView>(R.id.tvProfileName)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val displayName = state.user?.displayName ?: getString(R.string.profile_display_name)
                    name.text = displayName
                    avatar.bindProfileAvatar(state.customization.pictureId, displayName)
                }
            }
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
