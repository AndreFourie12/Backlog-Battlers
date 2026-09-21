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
import androidx.core.view.isNotEmpty
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.QueryRow
import com.backlogbattlers.app.adapters.QueryRowAdapter
import com.backlogbattlers.app.adapters.SearchResult
import com.backlogbattlers.app.adapters.SearchResultAdapter
import com.backlogbattlers.app.domain.model.BrowseCategory
import com.backlogbattlers.app.domain.model.SearchSort
import com.backlogbattlers.app.util.browseCategoryLabel
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.platformLabel
import com.backlogbattlers.app.util.searchSortLabel
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.GameListState
import com.backlogbattlers.app.viewmodels.SearchMode
import com.backlogbattlers.app.viewmodels.SearchUiState
import com.backlogbattlers.app.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

private enum class FilterChip { PLATFORM, GENRE, SORT }

class SearchFragment : Fragment() {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var searchField: View
    private lateinit var searchInput: EditText
    private lateinit var clearButton: View

    private lateinit var restingState: View
    private lateinit var typingState: View
    private lateinit var resultsState: View

    private lateinit var recentAdapter: QueryRowAdapter
    private lateinit var trendingAdapter: QueryRowAdapter
    private lateinit var suggestionAdapter: SearchResultAdapter
    private lateinit var resultAdapter: SearchResultAdapter

    private val filterChips = mutableMapOf<FilterChip, View>()

    private var updatingInput = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        restingState = view.findViewById(R.id.state_search_resting)
        typingState = view.findViewById(R.id.state_search_typing)
        resultsState = view.findViewById(R.id.state_search_results)

        bindSearchBar(view)
        bindRestingState()
        bindTypingState()
        bindResultsState()

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

        setSearchText(viewModel.uiState.value.query)

        if (searchInput.text.isEmpty()) {
            searchInput.requestFocus()
            showKeyboard()
        }
    }

    private fun bindSearchBar(view: View) {
        view.findViewById<View>(R.id.btn_search_back).setOnClickListener {
            hideKeyboard()
            findNavController().navigateUp()
        }

        searchField = view.findViewById(R.id.search_field)
        searchInput = view.findViewById(R.id.et_search)
        clearButton = view.findViewById(R.id.btn_clear_search)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (updatingInput) return
                viewModel.onQueryChanged(s?.toString().orEmpty())
            }
        })

        searchInput.setOnFocusChangeListener { _, _ -> updateFieldOutline() }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                viewModel.runSearch(searchInput.text.toString())
                true
            } else {
                false
            }
        }

        clearButton.setOnClickListener {
            setSearchText("")
            viewModel.clearQuery()
            searchInput.requestFocus()
            showKeyboard()
        }
    }

    private fun bindRestingState() {
        recentAdapter = QueryRowAdapter(
            onClick = { row -> replaySearch(row.label) },
            onRemove = { row -> viewModel.removeRecentSearch(row.label) },
        )
        trendingAdapter = QueryRowAdapter(
            onClick = { row -> row.gameId?.let { openGame(it) } },
        )

        restingState.findViewById<RecyclerView>(R.id.list_recent).adapter = recentAdapter
        restingState.findViewById<RecyclerView>(R.id.list_trending).adapter = trendingAdapter

        val container = restingState.findViewById<LinearLayout>(R.id.container_genres)
        BrowseCategory.entries.forEach { category ->
            val chip = container.inflateChild(R.layout.partial_chip) as TextView
            chip.setText(browseCategoryLabel(category))
            chip.setOnClickListener {
                hideKeyboard()
                setSearchText("")
                viewModel.browse(category)
            }
            if (container.isNotEmpty()) {
                (chip.layoutParams as LinearLayout.LayoutParams).marginStart =
                    resources.getDimensionPixelSize(R.dimen.list_gap)
            }
            container.addView(chip)
        }
    }

    private fun bindTypingState() {
        suggestionAdapter = SearchResultAdapter(
            itemLayout = R.layout.item_search_suggestion,
            onClick = { game -> openGame(game.gameId) },
            onAdd = { game -> viewModel.addToLibrary(game) },
        )
        typingState.findViewById<RecyclerView>(R.id.list_suggestions).adapter = suggestionAdapter

        typingState.findViewById<View>(R.id.row_search_for).setOnClickListener {
            hideKeyboard()
            viewModel.runSearch()
        }
    }

    private fun bindResultsState() {
        resultAdapter = SearchResultAdapter(
            itemLayout = R.layout.item_search_result,
            onClick = { game -> openGame(game.gameId) },
            onAdd = { game -> viewModel.addToLibrary(game) },
        )
        resultsState.findViewById<RecyclerView>(R.id.list_results).adapter = resultAdapter

        resultsState.findViewById<View>(R.id.btn_retry_results).setOnClickListener { viewModel.retry() }

        val container = resultsState.findViewById<LinearLayout>(R.id.container_filters)
        FilterChip.entries.forEach { kind ->
            val chip = container.inflateChild(R.layout.partial_filter_chip)
            chip.setOnClickListener { showFilterMenu(kind, chip) }
            if (container.isNotEmpty()) {
                (chip.layoutParams as LinearLayout.LayoutParams).marginStart =
                    resources.getDimensionPixelSize(R.dimen.list_gap)
            }
            container.addView(chip)
            filterChips[kind] = chip
        }
    }

    private fun render(state: SearchUiState) {
        restingState.setVisible(state.mode == SearchMode.RESTING)
        typingState.setVisible(state.mode == SearchMode.TYPING)
        resultsState.setVisible(state.mode == SearchMode.RESULTS)
        clearButton.setVisible(state.query.isNotEmpty() || state.browsedCategory != null)
        updateFieldOutline()

        when (state.mode) {
            SearchMode.RESTING -> renderResting(state)
            SearchMode.TYPING -> renderTyping(state)
            SearchMode.RESULTS -> renderResults(state)
        }
    }

    private fun renderResting(state: SearchUiState) {
        val recent = state.recentSearches.map { QueryRow(it, QueryRow.Kind.RECENT) }
        recentAdapter.submitList(recent)
        restingState.findViewById<View>(R.id.group_recent).setVisible(recent.isNotEmpty())

        val trending = state.trending.map { game ->
            QueryRow(game.title, QueryRow.Kind.TRENDING, gameId = game.gameId)
        }
        trendingAdapter.submitList(trending)
        restingState.findViewById<View>(R.id.group_trending).setVisible(trending.isNotEmpty())
    }

    private fun renderTyping(state: SearchUiState) {
        typingState.findViewById<TextView>(R.id.tv_search_for).text =
            getString(R.string.search_for_quoted, state.query)

        val list = typingState.findViewById<View>(R.id.list_suggestions)
        val progress = typingState.findViewById<View>(R.id.progress_suggestions)
        val message = typingState.findViewById<TextView>(R.id.tv_suggestions_message)

        progress.setVisible(state.suggestions is GameListState.Loading)

        val games = (state.suggestions as? GameListState.Loaded)?.games.orEmpty()
        list.setVisible(games.isNotEmpty())
        suggestionAdapter.submitList(games.map { SearchResult(it, it.gameId in state.ownedGameIds) })

        message.setVisible(state.suggestions is GameListState.Error)
        message.setText(R.string.search_error)
    }

    private fun renderResults(state: SearchUiState) {
        renderFilterChips(state)

        val list = resultsState.findViewById<View>(R.id.list_results)
        val progress = resultsState.findViewById<View>(R.id.progress_results)
        val count = resultsState.findViewById<TextView>(R.id.tv_result_count)
        val messageGroup = resultsState.findViewById<View>(R.id.state_results_message)
        val message = resultsState.findViewById<TextView>(R.id.tv_results_message)
        val messageBody = resultsState.findViewById<TextView>(R.id.tv_results_message_body)
        val retry = resultsState.findViewById<View>(R.id.btn_retry_results)

        val loading = state.results is GameListState.Loading
        progress.setVisible(loading)
        count.setVisible(state.results is GameListState.Loaded)

        val visible = state.visibleResults
        list.setVisible(visible.isNotEmpty())
        resultAdapter.submitList(visible.map { SearchResult(it, it.gameId in state.ownedGameIds) })

        count.text = resources.getQuantityString(R.plurals.search_result_count, visible.size, visible.size)

        val showMessage = !loading && visible.isEmpty()
        messageGroup.setVisible(showMessage)
        if (!showMessage) return

        when {
            state.results is GameListState.Error -> {
                message.setText(R.string.search_error)
                messageBody.setText(R.string.try_again_body)
                retry.setVisible(true)
            }

            state.filters.isNarrowing -> {
                message.setText(R.string.search_no_results_filtered)
                messageBody.setText(R.string.search_no_results_filtered_body)
                retry.setVisible(false)
            }

            else -> {
                message.text = getString(R.string.search_no_results, resultsSubject(state))
                messageBody.setText(R.string.search_no_results_body)
                retry.setVisible(false)
            }
        }
    }

    private fun renderFilterChips(state: SearchUiState) {
        bindFilterChip(
            kind = FilterChip.PLATFORM,
            label = state.filters.platform?.let { platformLabel(it) } ?: getString(R.string.filter_platform),
            active = state.filters.platform != null,
        )
        bindFilterChip(
            kind = FilterChip.GENRE,
            label = state.filters.genre ?: getString(R.string.filter_genre),
            active = state.filters.genre != null,
        )
        bindFilterChip(
            kind = FilterChip.SORT,
            label = if (state.filters.sort == SearchSort.RELEVANCE) {
                getString(R.string.filter_sort)
            } else {
                getString(searchSortLabel(state.filters.sort))
            },
            active = state.filters.sort != SearchSort.RELEVANCE,
        )
    }

    private fun bindFilterChip(kind: FilterChip, label: String, active: Boolean) {
        val chip = filterChips[kind] ?: return
        val text = chip.findViewById<TextView>(R.id.tv_filter_label)
        text.text = label
        text.setTextColor(
            ContextCompat.getColor(requireContext(), if (active) R.color.accent else R.color.text_secondary),
        )
        chip.setBackgroundResource(
            if (active) R.drawable.bg_pill_outline_selected else R.drawable.bg_pill_outline,
        )
    }

    private fun showFilterMenu(kind: FilterChip, anchor: View) {
        val state = viewModel.uiState.value
        val menu = PopupMenu(requireContext(), anchor)

        when (kind) {
            FilterChip.PLATFORM -> {
                menu.menu.add(R.string.filter_any_platform).setOnMenuItemClickListener {
                    viewModel.setPlatformFilter(null)
                    true
                }
                state.platformOptions.forEach { platform ->
                    menu.menu.add(platformLabel(platform)).setOnMenuItemClickListener {
                        viewModel.setPlatformFilter(platform)
                        true
                    }
                }
            }

            FilterChip.GENRE -> {
                menu.menu.add(R.string.filter_any_genre).setOnMenuItemClickListener {
                    viewModel.setGenreFilter(null)
                    true
                }
                state.genreOptions.forEach { genre ->
                    menu.menu.add(genre).setOnMenuItemClickListener {
                        viewModel.setGenreFilter(genre)
                        true
                    }
                }
            }

            FilterChip.SORT -> {
                SearchSort.entries.forEach { sort ->
                    menu.menu.add(searchSortLabel(sort)).setOnMenuItemClickListener {
                        viewModel.setSort(sort)
                        true
                    }
                }
            }
        }

        menu.show()
    }

    private fun resultsSubject(state: SearchUiState): String {
        val category = state.browsedCategory ?: return state.query
        return getString(browseCategoryLabel(category))
    }

    private fun replaySearch(query: String) {
        setSearchText(query)
        hideKeyboard()
        viewModel.runSearch(query)
    }

    private fun setSearchText(text: String) {
        updatingInput = true
        searchInput.setText(text)
        searchInput.setSelection(text.length)
        updatingInput = false
    }

    private fun updateFieldOutline() {
        val active = searchInput.hasFocus() || searchInput.text.isNotEmpty()
        searchField.setBackgroundResource(if (active) R.drawable.bg_field_active else R.drawable.bg_field)
    }

    private fun openGame(gameId: Int) {
        hideKeyboard()
        findNavController().navigate(
            R.id.action_searchFragment_to_gameDetailFragment,
            Bundle().apply { putString("gameId", gameId.toString()) },
        )
    }

    private fun showKeyboard() {
        searchInput.post {
            insetsController()?.show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun insetsController() =
        view?.let { WindowCompat.getInsetsController(requireActivity().window, it) }

    private fun hideKeyboard() {
        insetsController()?.hide(WindowInsetsCompat.Type.ime())
        searchInput.clearFocus()
    }
}
