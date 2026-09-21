package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

// display name editing. the email and avatar come from the google account, so
// they are shown but locked
class EditProfileFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    // set once from the stored user so later collections do not overwrite a
    // name the user is halfway through typing
    private var nameLoaded = false

    //------------------------------
    // inflates the edit profile fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    //------------------------------
    // fills the form from the signed in user and saves changes back to room
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barEditProfile)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_edit_profile)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        val nameField = view.findViewById<EditText>(R.id.etDisplayName)
        val emailLabel = view.findViewById<TextView>(R.id.tvProfileEmail)
        val saveButton = view.findViewById<TextView>(R.id.btnSaveProfile)

        saveButton.setOnClickListener { save(nameField) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentUser.collect { user ->
                    emailLabel.text = user?.email.orEmpty()
                    if (user != null && !nameLoaded) {
                        nameField.setText(user.displayName)
                        nameField.setSelection(nameField.text.length)
                        nameLoaded = true
                    }
                    // there is nothing to save to until someone is signed in
                    saveButton.isEnabled = user != null
                    saveButton.alpha = if (user != null) 1f else 0.5f
                }
            }
        }
    }

    //------------------------------
    // validates the name then writes it, the screen closes on success
    private fun save(nameField: EditText) {
        val name = nameField.text.toString().trim()

        when {
            name.isEmpty() -> {
                Toast.makeText(requireContext(), R.string.edit_profile_name_empty, Toast.LENGTH_SHORT).show()
                return
            }
            name.length > MAX_NAME_LENGTH -> {
                Toast.makeText(requireContext(), R.string.edit_profile_name_too_long, Toast.LENGTH_SHORT).show()
                return
            }
        }

        hideKeyboard(nameField)

        viewLifecycleOwner.lifecycleScope.launch {
            val saved = ServiceLocator.authRepository.updateDisplayName(name)
            if (!isAdded) return@launch

            if (saved) {
                Toast.makeText(requireContext(), R.string.edit_profile_saved, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } else {
                Toast.makeText(requireContext(), R.string.edit_profile_signed_out, Toast.LENGTH_LONG).show()
            }
        }
    }

    //------------------------------
    // drops the keyboard before the screen closes
    private fun hideKeyboard(field: View) {
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(field.windowToken, 0)
    }

    private companion object {
        const val MAX_NAME_LENGTH = 24
    }
}
//------------------------------EOF------------------------------\\
