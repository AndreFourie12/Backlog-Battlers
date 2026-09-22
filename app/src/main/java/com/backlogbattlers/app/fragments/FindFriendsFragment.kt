package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.FriendSearchResultAdapter
import com.backlogbattlers.app.domain.model.FriendRelationshipStatus
import com.backlogbattlers.app.viewmodels.FindFriendsViewModel
import kotlinx.coroutines.launch

// fragment for searching users by display name and sending friend requests
class FindFriendsFragment : Fragment() {

    private val viewModel: FindFriendsViewModel by viewModels()

    private lateinit var searchAdapter: FriendSearchResultAdapter

    //------------------------------
    // inflates the find friends fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_find_friends, container, false)
    }

    //------------------------------
    // initializes views, search listeners, adapter and collects ui state updates
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageButton>(R.id.btn_back)
        val etSearchQuery = view.findViewById<EditText>(R.id.et_search_query)
        val btnSearch = view.findViewById<ImageButton>(R.id.btn_search)
        val rvSearchResults = view.findViewById<RecyclerView>(R.id.rv_search_results)

        val includeEmptyState = view.findViewById<View>(R.id.include_empty_state)
        val tvEmptyTitle = includeEmptyState.findViewById<TextView>(R.id.tv_empty_title)
        val tvEmptyBody = includeEmptyState.findViewById<TextView>(R.id.tv_empty_body)

        val progressBarSearch = view.findViewById<ProgressBar>(R.id.progress_bar_search)

        searchAdapter = FriendSearchResultAdapter(
            onSendRequest = { viewModel.sendRequest(it.userId) },
        )

        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        rvSearchResults.adapter = searchAdapter

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnSearch.setOnClickListener {
            viewModel.search()
        }

        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onQueryChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSearchQuery.setOnEditorActionListener { _, actionId, _ ->

            // this if statement triggers search when the search action gets pressed
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search()
                true
            } else {
                false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progressBarSearch.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    if (state.errorMessage != null) {
                        Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
                    }

                    // this mapping upgrades results to pending sent when a request was sent in this session
                    val displayResults = state.results.map { result ->

                        // this if statement overrides friend relation status to a pending sent for newly requested users
                        if (result.userId in state.sentRequestUserIds && result.status == FriendRelationshipStatus.NONE) {
                            result.copy(status = FriendRelationshipStatus.PENDING_SENT)
                        } else {
                            result
                        }
                    }
                    searchAdapter.submitList(displayResults)

                    // this if statement displays prompt or no results copy based on whether a search was executed
                    if (!state.isLoading && state.results.isEmpty()) {
                        if (state.hasSearched) {
                            includeEmptyState.visibility = View.VISIBLE
                            tvEmptyTitle.text = getString(R.string.no_search_results_title)
                            tvEmptyBody.text = getString(R.string.no_search_results_body)
                        } else if (state.query.isNotBlank()) {
                            includeEmptyState.visibility = View.VISIBLE
                            tvEmptyTitle.text = getString(R.string.search_friends_prompt_title)
                            tvEmptyBody.text = getString(R.string.search_friends_prompt_body)
                        } else {
                            includeEmptyState.visibility = View.GONE
                        }
                    } else {
                        includeEmptyState.visibility = View.GONE
                    }
                }
            }
        }
    }
}
//------------------------------EOF------------------------------\\