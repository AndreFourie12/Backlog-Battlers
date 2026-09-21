package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.SearchResult
import com.backlogbattlers.app.adapters.SearchResultAdapter
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.GameListState
import com.backlogbattlers.app.viewmodels.GameViewModel
import com.backlogbattlers.app.viewmodels.GamesUiState
import kotlinx.coroutines.launch


class GamesFragment : Fragment() {

    private val viewModel: GameViewModel by viewModels()

    private lateinit var searchInput: EditText
    private lateinit var countText: TextView
    private lateinit var emptyCard: View
    private lateinit var popularList: View
    private lateinit var popularProgress: View
    private lateinit var popularError: View

    private lateinit var popularAdapter: SearchResultAdapter

    // inflates the games fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_games, container, false)
    }

    // wires the search box, the add first game button and the popular list, then follows the view model
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.et_library_search)
        countText = view.findViewById(R.id.tv_library_count)
        emptyCard = view.findViewById(R.id.card_library_empty)
        popularList = view.findViewById(R.id.list_popular)
        popularProgress = view.findViewById(R.id.progress_popular)
        popularError = view.findViewById(R.id.state_popular_error)

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        view.findViewById<View>(R.id.btn_add_first_game).setOnClickListener {
            hideKeyboard()
            findNavController().navigate(R.id.action_gamesFragment_to_searchFragment)
        }

        view.findViewById<View>(R.id.btn_retry_popular).setOnClickListener {
            viewModel.loadPopular()
        }

        popularAdapter = SearchResultAdapter(
            itemLayout = R.layout.item_popular_game,
            onClick = { game -> openGame(game.gameId) },
            onAdd = { game -> viewModel.addToLibrary(game) },
        )
        view.findViewById<RecyclerView>(R.id.list_popular).adapter = popularAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.addedGames.collect { title ->
                    Toast.makeText(requireContext(), getString(R.string.added_to_library, title), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    // shows the library count, the empty state card while there are no games, and the popular list
    private fun render(state: GamesUiState) {
        val count = state.libraryCount

        // both hold their place until the library has been read, so nothing below them jumps
        countText.visibility = if (count == null) View.INVISIBLE else View.VISIBLE
        if (count != null) {
            countText.text = resources.getQuantityString(R.plurals.library_game_count, count, count)
        }
        emptyCard.visibility = when (count) {
            null -> View.INVISIBLE
            0 -> View.VISIBLE
            else -> View.GONE
        }

        val games = (state.popular as? GameListState.Loaded)?.games.orEmpty()
        popularProgress.setVisible(state.popular is GameListState.Loading)
        popularError.setVisible(state.popular is GameListState.Error)
        popularList.setVisible(games.isNotEmpty())
        popularAdapter.submitList(games.map { SearchResult(it, it.gameId in state.ownedGameIds) })
    }

    private fun openGame(gameId: Int) {
        hideKeyboard()
        findNavController().navigate(
            R.id.action_gamesFragment_to_gameDetailFragment,
            Bundle().apply { putString("gameId", gameId.toString()) },
        )
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(requireActivity().window, searchInput).hide(WindowInsetsCompat.Type.ime())
        searchInput.clearFocus()
    }
}
