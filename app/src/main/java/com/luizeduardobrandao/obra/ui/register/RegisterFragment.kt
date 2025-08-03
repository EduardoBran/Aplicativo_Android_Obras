package com.luizeduardobrandao.obra.ui.register

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
import com.luizeduardobrandao.obra.databinding.FragmentRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
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
    private fun setupToolbar() = binding.toolbarRegister.apply {
        title = getString(R.string.register_title)
        setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupListeners() = with(binding) {

        btnRegister.setOnClickListener {
            root.hideKeyboard()

            btnRegister.isEnabled = false    // evita duplo-clique
            progressRegister.isVisible = true

            viewModel.register(
                name = etName.text?.toString().orEmpty(),
                email = etEmailReg.text?.toString().orEmpty(),
                password = etPasswordReg.text?.toString().orEmpty()
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Estado da operação de cadastro
                launch {
                    viewModel.state.collect { state ->
                        when (state) {
                            is UiState.Idle -> resetUi()
                            is UiState.Loading -> { }
                            is UiState.Success -> {
                                resetUi()
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.register_toast_success),
                                    Toast.LENGTH_SHORT
                                ).show()

                                // navega para WorkFragment
                                findNavController().navigate(
                                    RegisterFragmentDirections.actionRegisterToWork()
                                )
                            }

                            is UiState.ErrorRes -> {
                                resetUi()
                                showSnackbarFragment(
                                    type = Constants.SnackType.ERROR.name,
                                    title = getString(R.string.snack_error),
                                    msg = getString(state.resId),
                                    btnText = getString(R.string.snack_button_ok)
                                )
                            }

                            is UiState.Error -> {      // caso use mensagem livre
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

    // Restaura botões / progress-bar para estado neutro.
    private fun resetUi() = with(binding) {
        progressRegister.isGone = true
        btnRegister.isEnabled = true
    }
}