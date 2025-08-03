package com.luizeduardobrandao.obra.ui.resetpassword

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResetPasswordBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResetPasswordViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ───────────────────────────── HELPERS ────────────────────────────────

    private fun setupToolbar() = binding.toolbarReset.apply {
        title = getString(R.string.reset_title)
        setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupListeners() = with(binding) {
        btnReset.setOnClickListener {
            root.hideKeyboard()

            btnReset.isEnabled = false
            progressReset.isVisible = true

            viewModel.sendResetEmail(etEmailReset.text?.toString().orEmpty())
        }
    }

    private fun observeViewModel(){
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.state.collect { state ->
                        when (state) {
                            is UiState.Idle -> resetUi()
                            is UiState.Loading -> { /* já tratado */ }

                            is UiState.Success -> {
                                resetUi()
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.reset_toast_success),
                                    Toast.LENGTH_LONG
                                ).show()

                                // volta para LoginFragment
                                findNavController().navigateUp()
                            }

                            is UiState.ErrorRes -> {
                                resetUi()
                                showSnackbarFragment(
                                    type    = Constants.SnackType.ERROR.name,
                                    title   = getString(R.string.snack_error),
                                    msg     = getString(state.resId),
                                    btnText = getString(R.string.snack_button_ok)
                                )
                            }

                            is UiState.Error -> {
                                resetUi()
                                showSnackbarFragment(
                                    type    = Constants.SnackType.ERROR.name,
                                    title   = getString(R.string.snack_error),
                                    msg     = state.message,
                                    btnText = getString(R.string.snack_button_ok)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resetUi() = with(binding) {
        progressReset.isGone = true
        btnReset.isEnabled = true
    }
}