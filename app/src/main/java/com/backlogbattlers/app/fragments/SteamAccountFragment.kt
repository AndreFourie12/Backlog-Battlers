package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.viewmodels.SteamAccountViewModel
import kotlinx.coroutines.launch

// link or unlink the Steam account that achievements are checked against
class SteamAccountFragment : Fragment() {

    private val viewModel: SteamAccountViewModel by viewModels()

    //------------------------------
    // inflates the Steam account layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_steam_account, container, false)
    }

    //------------------------------
    // wires the buttons, then shows whichever of loading, error, linked or unlinked the state is in
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barSteam)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_steam_account)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        val progress = view.findViewById<View>(R.id.progressSteam)
        val errorGroup = view.findViewById<View>(R.id.groupSteamError)
        val linkedGroup = view.findViewById<View>(R.id.groupSteamLinked)
        val unlinkedGroup = view.findViewById<View>(R.id.groupSteamUnlinked)
        val linkedId = view.findViewById<TextView>(R.id.tvSteamLinkedId)
        val input = view.findViewById<EditText>(R.id.etSteamId)
        val linkButton = view.findViewById<View>(R.id.btnSteamLink)
        val unlinkButton = view.findViewById<View>(R.id.btnSteamUnlink)
        val importButton = view.findViewById<View>(R.id.btnSteamImport)
        val importProgress = view.findViewById<View>(R.id.progressSteamImport)

        view.findViewById<View>(R.id.btnSteamRetry).setOnClickListener { viewModel.load() }
        linkButton.setOnClickListener { viewModel.link(input.text.toString()) }
        unlinkButton.setOnClickListener { viewModel.unlink() }
        importButton.setOnClickListener { viewModel.importGames() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) viewModel.link(input.text.toString())
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    errorGroup.visibility = if (state.loadFailed) View.VISIBLE else View.GONE
                    val ready = !state.isLoading && !state.loadFailed
                    linkedGroup.visibility = if (ready && state.linkedSteamId != null) View.VISIBLE else View.GONE
                    unlinkedGroup.visibility = if (ready && state.linkedSteamId == null) View.VISIBLE else View.GONE
                    linkedId.text = state.linkedSteamId
                    // once linked, the typed ID is no longer needed and shouldn't reappear after an unlink
                    if (state.linkedSteamId != null) input.text.clear()

                    // a request in flight can't be repeated by a second tap
                    linkButton.isEnabled = !state.isSaving
                    unlinkButton.isEnabled = !state.isSaving && !state.isImporting
                    importButton.isEnabled = !state.isImporting && !state.isSaving
                    importProgress.visibility = if (state.isImporting) View.VISIBLE else View.GONE

                    // this if statement shows each message once, then tells the view model it was shown
                    if (state.message != null) {
                        val text = getString(state.message, *state.messageArgs.toTypedArray())
                        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
                        viewModel.messageShown()
                    }
                }
            }
        }
    }
}
//------------------------------EOF------------------------------\\
