package com.luizeduardobrandao.obra.ui.login

import androidx.fragment.app.viewModels
import android.os.Bundle
import android.net.ConnectivityManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.LottieDrawable
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentLoginBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.applyFullWidthButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.ButtonSizingConfig
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.isConnectedToInternet
import com.luizeduardobrandao.obra.utils.LoginLinksAligner
import com.luizeduardobrandao.obra.utils.registerConnectivityCallback
import com.luizeduardobrandao.obra.utils.unregisterConnectivityCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    // Animação Imagem
    private var hasPlayedEnterAnim = false

    // Conexão a internet
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    // Flag desaparecer imagem orientação horizontal
    private var lastHeroVisible: Boolean = false

    // Animação Imagem
    private val enterAnimTime = 2.2f
    private fun scaled(ms: Long) = (ms * enterAnimTime).toLong()

    // Animação Lottie
    private var didNavigateAfterAnim = false
    private var finalAnimListener: AnimatorListenerAdapter? = null

    override fun onStart() {
        super.onStart()

        // Checagem imediata ao entrar na tela
        if (!requireContext().isConnectedToInternet()) {
            showSnackbarFragment(
                Constants.SnackType.WARNING.name,
                getString(R.string.generic_warning),
                getString(R.string.generic_error_no_internet),
                getString(R.string.generic_ok_upper_case)
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
                        getString(R.string.generic_warning),
                        getString(R.string.generic_error_no_internet),
                        getString(R.string.generic_ok_upper_case)
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

        // Config especial para botão "Entrar"
        val loginButtonConfig = ButtonSizingConfig(
            minPaddingVdp = 14,   // vertical mínimo maior
            maxPaddingVdp = 20,   // vertical máximo maior
            minTextSp = 16,       // fonte mínima maior
            maxTextSp = 26        // fonte máxima maior
        )

        binding.btnLogin.applyFullWidthButtonSizingGrowShrink(config = loginButtonConfig)

        // 1) Aplica visibilidade do hero para este contexto (portrait/land, phone/tablet)
        val showHeroNow = resources.getBoolean(R.bool.show_hero)
        binding.imgLogin.visibility = if (showHeroNow) View.VISIBLE else View.GONE
        lastHeroVisible = showHeroNow

        // 2) Recupera flags antigas
        hasPlayedEnterAnim = savedInstanceState?.getBoolean(KEY_HAS_PLAYED_ENTER_ANIM) ?: false
        val prevHeroVisible = savedInstanceState?.getBoolean(KEY_PREV_HERO_VISIBLE)

        // 3) Regras de animação:
        // - Primeira vez: anima (como já era)
        // - Se antes o hero estava oculto (landscape) e agora ficou visível (portrait), anima de novo
        val shouldAnimateNow =
            !hasPlayedEnterAnim || (prevHeroVisible == false && showHeroNow)

        if (shouldAnimateNow) {
            view.doOnPreDraw {
                runEnterAnimation()
                hasPlayedEnterAnim = true
            }
        }

        setupListeners()  // cliques e text-changes
        collectUiState()  // estados do ViewModel
        LoginLinksAligner.applyLandscapeLinkFixes(binding)
        LoginLinksAligner.applyPortraitMirrorRightGapAsLeftMargin(binding) // só portrait


    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_PLAYED_ENTER_ANIM, hasPlayedEnterAnim)
        outState.putBoolean(KEY_PREV_HERO_VISIBLE, lastHeroVisible)
    }

    override fun onDestroyView() {
        binding.lottieLogin.removeAllAnimatorListeners()
        finalAnimListener = null
        _binding = null
        super.onDestroyView()
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
                        is UiState.Success -> playFinalAnimThenNavigate()
                    }
                }
            }
        }
    }


    /* ---------- Helper methods ---------- */
    private fun showIdle() = with(binding) {
        loginOverlay.isGone = true
        lottieLogin.cancelAnimation()
        btnLogin.isEnabled = true
        etEmail.isEnabled = true
        etPassword.isEnabled = true
        tvCreateAccount.isGone = false
        tvForgotPassword.isGone = false
    }

    private fun showLoading() = with(binding) {
        loginOverlay.isVisible = true
        lottieLogin.repeatCount = ValueAnimator.INFINITE
        lottieLogin.repeatMode = LottieDrawable.RESTART
        lottieLogin.playAnimation()

        btnLogin.isEnabled = false
        etEmail.isEnabled = false
        etPassword.isEnabled = false
        tvCreateAccount.isGone = true
        tvForgotPassword.isGone = true
    }

    private fun showError(message: String) {
        binding.loginOverlay.isGone = true
        binding.lottieLogin.cancelAnimation()
        binding.btnLogin.isEnabled = true
        binding.etEmail.isEnabled = true
        binding.etPassword.isEnabled = true
        showSnackbarFragment(
            type = Constants.SnackType.ERROR.name,
            title = getString(R.string.generic_error),
            msg = message,
            btnText = getString(R.string.generic_ok_upper_case)
        )
        viewModel.resetState()
    }

    // Navega para WorkFragment e mostra Toast.
    private fun navigateToWork() {
        Toast.makeText(requireContext(), R.string.login_toast_success, Toast.LENGTH_LONG).show()

        // direção gerada pelo Safe-Args
        val directions = LoginFragmentDirections.actionLoginToWork()
        findNavController().navigate(directions)
    }

    // Fundo de Animação para Imagem
    private fun runEnterAnimation() = with(binding) {
        val d = resources.displayMetrics.density

        // Distancias maiores para "sentir" o movimento
        val heroDy = -72f * d    // antes -16dp
        val formDy = 36f * d     // antes 16dp

        // Interpoladores mais "lentos" no final
        val slowOut = android.view.animation.DecelerateInterpolator(2f)
        val slowInOut = FastOutSlowInInterpolator()

        // HERO — slide down + fade + leve zoom-in
        if (imgLogin.isVisible) {
            imgLogin.alpha = 0f
            imgLogin.translationY = heroDy
            imgLogin.scaleX = 0.94f
            imgLogin.scaleY = 0.94f
            imgLogin.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(scaled(800L))   // ↑ bem mais devagar
                .setStartDelay(scaled(100L))
                .setInterpolator(slowOut)
                .withLayer()
                .start()
        }

        // FORM — entra depois (stagger)
        formContainer.alpha = 0f
        formContainer.translationY = formDy
        formContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(scaled(650L))
            .setStartDelay(scaled(350L))    // começa após o hero
            .setInterpolator(slowInOut)
            .withLayer()
            .start()

        // LINKS — por último, só fade-in prolongado
        linksRow.alpha = 0f
        linksRow.animate()
            .alpha(1f)
            .setDuration(scaled(450L))
            .setStartDelay(scaled(800L))
            .withLayer()
            .start()
    }

    // Animação 1 ciclo Lottie
    private fun playFinalAnimThenNavigate() = with(binding) {
        if (didNavigateAfterAnim) return@with

        loginOverlay.isVisible = true

        // Remova listeners antigos, mas NÃO cancele nem zere o progresso
        lottieLogin.removeAllAnimatorListeners()

        // Tira do loop e deixa concluir o ciclo atual até o fim
        lottieLogin.repeatCount = 0
        lottieLogin.repeatMode = LottieDrawable.RESTART

        // Garante que vai do frame atual até 1f (sem "pulo" pro começo)
        val current = lottieLogin.progress.coerceIn(0f, 1f)
        val start = if (current >= 0.95f) 0.75f else current
        try {
            lottieLogin.setMinAndMaxProgress(start, 1f)
        } catch (_: Throwable) {
            // Compat com versões antigas do Lottie
            lottieLogin.setMinProgress(start)
            lottieLogin.setMaxProgress(1f)
        }

        // >>> SUBSTITUA seu listener por este <<<
        finalAnimListener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!didNavigateAfterAnim) {
                    didNavigateAfterAnim = true

                    // (opcional) reset do range para a próxima vez
                    try {
                        lottieLogin.setMinAndMaxProgress(0f, 1f)
                    } catch (_: Throwable) {
                    }

                    loginOverlay.animate()
                        .alpha(0f)
                        .setDuration(180L)
                        .withEndAction {
                            loginOverlay.isGone = true
                            loginOverlay.alpha = 1f
                            navigateToWork()
                        }
                        .start()
                }
            }
        }
        lottieLogin.addAnimatorListener(finalAnimListener)

        // Se por acaso não estiver animando, inicia
        if (!lottieLogin.isAnimating) {
            lottieLogin.playAnimation()
        }
    }

    companion object {
        private const val KEY_HAS_PLAYED_ENTER_ANIM = "hasPlayedEnterAnim"
        private const val KEY_PREV_HERO_VISIBLE = "prevHeroVisible"
    }
}