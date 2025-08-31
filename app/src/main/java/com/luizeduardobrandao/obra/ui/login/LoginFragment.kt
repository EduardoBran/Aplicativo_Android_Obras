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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.net.ConnectivityManager
import com.luizeduardobrandao.obra.utils.isConnectedToInternet
import com.luizeduardobrandao.obra.utils.registerConnectivityCallback
import com.luizeduardobrandao.obra.utils.unregisterConnectivityCallback
import androidx.core.view.doOnPreDraw
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    // Animação Imagem
    private var hasPlayedEnterAnim = false

    // Conexão a internet
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStart() {
        super.onStart()

        // Checagem imediata ao entrar na tela
        if (!requireContext().isConnectedToInternet()) {
            showSnackbarFragment(
                Constants.SnackType.WARNING.name,
                getString(R.string.snack_warning),
                getString(R.string.error_no_internet),
                getString(R.string.snack_button_ok)
            )
        }

        // Observa mudanças de conectividade enquanto a tela está visível
        netCallback = requireContext().registerConnectivityCallback(
            onAvailable = {
                // opcional: pode ocultar um aviso, ou ignorar
            },
            onLost = {
                // Mostrar snackbar quando perder internet
                if (view != null) {
                    showSnackbarFragment(
                        Constants.SnackType.WARNING.name,
                        getString(R.string.snack_warning),
                        getString(R.string.error_no_internet),
                        getString(R.string.snack_button_ok)
                    )
                }
            }
        )
    }

    override fun onStop() {
        super.onStop()
        netCallback?.let { requireContext().unregisterConnectivityCallback(it) }
        netCallback = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hasPlayedEnterAnim = savedInstanceState?.getBoolean("hasPlayedEnterAnim") ?: false
        if (!hasPlayedEnterAnim) {
            // roda depois do layout medir as views (evita flicker)
            view.doOnPreDraw {
                runEnterAnimation()
                hasPlayedEnterAnim = true
            }
        }

        setupListeners()  // cliques e text-changes
        collectUiState()  // estados do ViewModel
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("hasPlayedEnterAnim", hasPlayedEnterAnim)
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
        tvCreateAccount.isGone = true
        tvForgotPassword.isGone = true
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
        viewModel.resetState()
    }

    // Navega para WorkFragment e mostra Toast.
    private fun navigateToWork() {
        Toast.makeText(requireContext(), R.string.login_toast_success, Toast.LENGTH_SHORT).show()

        // direção gerada pelo Safe-Args
        val directions = LoginFragmentDirections.actionLoginToWork()
        findNavController().navigate(directions)
    }

    // Fundo de Animação para Imagem
    private fun runEnterAnimation() = with(binding) {
        val interp = FastOutSlowInInterpolator()

        // deslocamento leve em dp
        val dy = 16f * resources.displayMetrics.density

        // estado inicial (antes de aparecer)
        imgLogin.alpha = 0f
        imgLogin.translationY = -dy

        formContainer.alpha = 0f
        formContainer.translationY = dy

        // anima "hero" (imagem)
        imgLogin.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setStartDelay(40L)
            .setInterpolator(interp)
            .start()

        // anima formulário (campos + botão + links)
        formContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .setStartDelay(80L)
            .setInterpolator(interp)
            .start()
    }
}