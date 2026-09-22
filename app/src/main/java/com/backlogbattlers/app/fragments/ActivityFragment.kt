package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R

// the activity feed.
// the standings card and the day grouped feed below it are laid out in
// fragment_activity, so all this has to do is send the card's button to
// the leaderboard.
class ActivityFragment : Fragment() {

    // inflates the activity fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_activity, container, false)
    }

    // MainActivity already pads the root with the status bar inset for this
    // destination, so the only wiring left is the leaderboard button
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_view_standings).setOnClickListener {
            findNavController().navigate(R.id.action_activityFragment_to_standingsFragment)
        }
    }
}
//------------------------------EOF------------------------------\\
