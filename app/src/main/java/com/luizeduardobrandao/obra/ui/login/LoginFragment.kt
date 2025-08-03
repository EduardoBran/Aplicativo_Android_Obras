package com.luizeduardobrandao.obra.ui.login

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
import com.luizeduardobrandao.obra.databinding.FragmentLoginBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()  // cliques e text-changes
        collectUiState()  // estados do ViewModel
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null           // evita leaks
    }


    /* ---------- Setup de listeners ---------- */
    private fun setupListeners() = with(binding) {

        // Botão Entrar
        btnLogin.setOnClickListener {
            // esconde teclado
            root.hideKeyboard()

            val email = etEmail.text?.toString().orEmpty()
            val pass = etPassword.text?.toString().orEmpty()
            viewModel.login(email, pass)
        }

        // Link "Criar conta"
        tvCreateAccount.setOnClickListener {
            findNavController().navigate(
                LoginFragmentDirections.actionLoginToRegister()
            )
        }

        // Link "Esqueceu a senha"
        tvForgotPassword.setOnClickListener {
            findNavController().navigate(
                LoginFragmentDirections.actionLoginToReset()
            )
        }
    }

    /* ---------- Observa UiState<AuthResult> ---------- */
    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.state.collect { state ->
                    when (state) {
                        UiState.Idle -> showIdle()
                        UiState.Loading -> showLoading()
                        is UiState.ErrorRes -> showError(getString(state.resId))
                        is UiState.Error -> showError(state.message)
                        is UiState.Success -> navigateToWork()
                    }
                }
            }
        }
    }


    /* ---------- Helper methods ---------- */
    private fun showIdle() = with(binding) {
        progressLogin.isGone = true
        btnLogin.isEnabled = true
    }

    private fun showLoading() = with(binding) {
        progressLogin.isVisible = true
        btnLogin.isEnabled = false                 // evita múltiplos cliques
    }

    private fun showError(message: String) {
        binding.progressLogin.isGone = true
        binding.btnLogin.isEnabled = true
        showSnackbarFragment(
            type = Constants.SnackType.ERROR.name,
            title = getString(R.string.snack_error),
            msg = message,
            btnText = getString(R.string.snack_button_ok)
        )
    }

    // Navega para WorkFragment e mostra Toast.
    private fun navigateToWork() {
        Toast.makeText(requireContext(), R.string.login_toast_success, Toast.LENGTH_SHORT).show()

        // direção gerada pelo Safe-Args
        val directions = LoginFragmentDirections.actionLoginToWork()
        findNavController().navigate(directions)
    }
}