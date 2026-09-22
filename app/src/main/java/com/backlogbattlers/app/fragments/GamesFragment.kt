package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isNotEmpty
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.LibraryGameAdapter
import com.backlogbattlers.app.adapters.SearchResult
import com.backlogbattlers.app.adapters.SearchResultAdapter
import com.backlogbattlers.app.domain.model.LibraryGame
import com.backlogbattlers.app.domain.model.LibrarySort
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.util.addGridSpacing
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.librarySortLabel
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.GameListState
import com.backlogbattlers.app.viewmodels.GameViewModel
import com.backlogbattlers.app.viewmodels.GamesUiState
import kotlinx.coroutines.launch

// the four status chips above the library list. null stands for the "All" chip: every status shown
private enum class LibraryFilterChip(val status: LibraryStatus?, val labelRes: Int) {
    ALL(null, R.string.library_filter_all),
    IN_PROGRESS(LibraryStatus.PLAYING, R.string.library_filter_in_progress),
    COMPLETED(LibraryStatus.COMPLETED, R.string.library_filter_completed),
    NOT_STARTED(LibraryStatus.BACKLOG, R.string.library_filter_not_started),
}

// the games tab, the user's library.
// while the library is empty it invites them to add a first game (which opens search) and lists a few
// popular games they can add straight away. Once it has games, the same screen shows them instead:
// searchable, filterable by status, and sortable
class GamesFragment : Fragment() {

    private val viewModel: GameViewModel by viewModels()

    private lateinit var searchInput: EditText
    private lateinit var sortButton: View
    private lateinit var countText: TextView
    private lateinit var emptyCard: View
    private lateinit var popularTitle: View
    private lateinit var popularSection: View
    private lateinit var popularList: View
    private lateinit var popularProgress: View
    private lateinit var popularError: View
    private lateinit var filtersScroll: View
    private lateinit var libraryState: View
    private lateinit var libraryList: View
    private lateinit var noMatchesState: View
    private lateinit var noMatchesText: TextView

    private lateinit var popularAdapter: SearchResultAdapter
    private lateinit var libraryAdapter: LibraryGameAdapter

    private val filterChips = mutableMapOf<LibraryFilterChip, TextView>()

    // inflates the games fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_games, container, false)
    }

    // wires the search box, the add first game button, the popular list and the library list, then follows the view model
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.et_library_search)
        sortButton = view.findViewById(R.id.btn_library_sort)
        countText = view.findViewById(R.id.tv_library_count)
        emptyCard = view.findViewById(R.id.card_library_empty)
        popularTitle = view.findViewById(R.id.tv_popular_title)
        popularSection = view.findViewById(R.id.section_popular)
        popularList = view.findViewById(R.id.list_popular)
        popularProgress = view.findViewById(R.id.progress_popular)
        popularError = view.findViewById(R.id.state_popular_error)
        filtersScroll = view.findViewById(R.id.scroll_library_filters)
        libraryState = view.findViewById(R.id.state_library_games)
        libraryList = view.findViewById(R.id.list_library_games)
        noMatchesState = view.findViewById(R.id.state_library_no_matches)
        noMatchesText = view.findViewById(R.id.tv_library_no_matches)

        bindSearchBar()
        bindLibraryFilters(view)

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

        libraryAdapter = LibraryGameAdapter(
            onClick = { libraryGame -> openGame(libraryGame.game.gameId) },
        )
        view.findViewById<RecyclerView>(R.id.list_library_games).apply {
            adapter = libraryAdapter
            addGridSpacing(spanCount = 2)
        }

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

    // the box doubles as the library filter once the library has games: every keystroke narrows the list
    private fun bindSearchBar() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                viewModel.onLibraryQueryChanged(s?.toString().orEmpty())
            }
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        sortButton.setOnClickListener { showSortMenu(it) }
    }

    // builds the four status chips once; render() only ever restyles them
    private fun bindLibraryFilters(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.container_library_filters)
        LibraryFilterChip.entries.forEach { chip ->
            val chipView = container.inflateChild(R.layout.partial_chip) as TextView
            chipView.setText(chip.labelRes)
            chipView.setOnClickListener { viewModel.setStatusFilter(chip.status) }
            if (container.isNotEmpty()) {
                (chipView.layoutParams as LinearLayout.LayoutParams).marginStart =
                    resources.getDimensionPixelSize(R.dimen.list_gap)
            }
            container.addView(chipView)
            filterChips[chip] = chipView
        }
    }

    private fun showSortMenu(anchor: View) {
        val menu = PopupMenu(requireContext(), anchor)
        LibrarySort.entries.forEach { sort ->
            menu.menu.add(librarySortLabel(sort)).setOnMenuItemClickListener {
                viewModel.setLibrarySort(sort)
                true
            }
        }
        menu.show()
    }

    // shows the library count, then one of two screens: the empty state with its popular games, or the
    // library list with its filters, for whichever the library actually is
    private fun render(state: GamesUiState) {
        val count = state.libraryCount
        val isEmpty = count == 0
        val isPopulated = count != null && count > 0

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

        renderPopular(state, visible = isEmpty)

        sortButton.setVisible(isPopulated)
        filtersScroll.setVisible(isPopulated)
        libraryState.setVisible(isPopulated)
        if (isPopulated) {
            renderLibraryFilters(state)
            renderLibraryGames(state)
        }
    }

    private fun renderPopular(state: GamesUiState, visible: Boolean) {
        popularTitle.setVisible(visible)
        popularSection.setVisible(visible)
        if (!visible) return

        val games = (state.popular as? GameListState.Loaded)?.games.orEmpty()
        popularProgress.setVisible(state.popular is GameListState.Loading)
        popularError.setVisible(state.popular is GameListState.Error)
        popularList.setVisible(games.isNotEmpty())
        popularAdapter.submitList(games.map { SearchResult(it, it.gameId in state.ownedGameIds) })
    }

    private fun renderLibraryFilters(state: GamesUiState) {
        LibraryFilterChip.entries.forEach { chip ->
            val chipView = filterChips[chip] ?: return@forEach
            val selected = state.statusFilter == chip.status
            chipView.setBackgroundResource(if (selected) R.drawable.bg_pill_accent else R.drawable.bg_pill_outline)
            chipView.setTextColor(
                ContextCompat.getColor(requireContext(), if (selected) R.color.on_accent else R.color.text_secondary),
            )
        }
    }

    private fun renderLibraryGames(state: GamesUiState) {
        val visible: List<LibraryGame> = state.visibleLibraryGames
        libraryList.setVisible(visible.isNotEmpty())
        libraryAdapter.submitList(visible)

        noMatchesState.setVisible(visible.isEmpty())
        if (visible.isEmpty()) {
            noMatchesText.text = if (state.libraryQuery.isNotBlank()) {
                getString(R.string.library_no_matches, state.libraryQuery)
            } else {
                getString(R.string.library_no_results)
            }
        }
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
