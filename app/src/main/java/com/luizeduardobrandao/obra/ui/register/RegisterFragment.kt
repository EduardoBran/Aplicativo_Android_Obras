package com.luizeduardobrandao.obra.ui.register

import androidx.fragment.app.viewModels
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.applyFullWidthButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.ButtonSizingConfig
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.airbnb.lottie.LottieDrawable

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    private var hasPlayedEnterAnim = false
    private var lastHeroVisible: Boolean = false

    // Animação Imagem
    private val enterAnimTime = 2.2f
    private fun scaled(ms: Long) = (ms * enterAnimTime).toLong()

    // Lottie / navegação
    private var didNavigateAfterAnim = false
    private var finalAnimListener: AnimatorListenerAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Responsividade botão
        val registerButtonConfig = ButtonSizingConfig(
            minPaddingVdp = 14,   // igual ao Login (CTA mais “pesado”)
            maxPaddingVdp = 20,
            minTextSp = 16,
            maxTextSp = 26
        )
        binding.btnRegister.applyFullWidthButtonSizingGrowShrink(config = registerButtonConfig)

        // 1) Aplica visibilidade do HERO conforme bool de recursos
        val showHeroNow = resources.getBoolean(R.bool.show_hero)
        binding.imgRegister.visibility = if (showHeroNow) View.VISIBLE else View.GONE
        lastHeroVisible = showHeroNow

        // 2) Recupera flags antigas
        hasPlayedEnterAnim = savedInstanceState?.getBoolean(KEY_HAS_PLAYED_ENTER_ANIM) ?: false
        val prevHeroVisible = savedInstanceState?.getBoolean(KEY_PREV_HERO_VISIBLE)

        // 3) Regra: anima se for a primeira vez OU se antes estava oculto e agora está visível
        val shouldAnimateNow = (!hasPlayedEnterAnim) || (prevHeroVisible == false && showHeroNow)

        if (shouldAnimateNow) {
            view.doOnPreDraw {
                runEnterAnimation()
                hasPlayedEnterAnim = true
            }
        }

        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_PLAYED_ENTER_ANIM, hasPlayedEnterAnim)
        outState.putBoolean(KEY_PREV_HERO_VISIBLE, lastHeroVisible)
    }

    override fun onDestroyView() {
        binding.lottieRegister.removeAllAnimatorListeners()
        finalAnimListener = null
        _binding = null
        super.onDestroyView()
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
                            is UiState.Idle -> showIdle()
                            is UiState.Loading -> showLoading()
                            is UiState.Success -> playFinalAnimThenNavigate()
                            is UiState.ErrorRes -> {
                                showIdle()
                                showSnackbarFragment(
                                    type = Constants.SnackType.ERROR.name,
                                    title = getString(R.string.snack_error),
                                    msg = getString(state.resId),
                                    btnText = getString(R.string.snack_button_ok)
                                )
                            }

                            is UiState.Error -> {
                                showIdle()
                                showSnackbarFragment(
                                    type = Constants.SnackType.ERROR.name,
                                    title = getString(R.string.snack_error),
                                    msg = state.message,
                                    btnText = getString(R.string.snack_button_ok)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showIdle() = with(binding) {
        registerOverlay.isGone = true
        lottieRegister.cancelAnimation()
        btnRegister.isEnabled = true
        etName.isEnabled = true
        etEmailReg.isEnabled = true
        etPasswordReg.isEnabled = true
    }

    private fun showLoading() = with(binding) {
        registerOverlay.isVisible = true
        lottieRegister.repeatCount = ValueAnimator.INFINITE
        lottieRegister.repeatMode = LottieDrawable.RESTART
        lottieRegister.playAnimation()

        btnRegister.isEnabled = false
        etName.isEnabled = false
        etEmailReg.isEnabled = false
        etPasswordReg.isEnabled = false
    }

    private fun navigateToWork() {
        Toast.makeText(requireContext(), R.string.register_toast_success, Toast.LENGTH_SHORT).show()
        findNavController().navigate(
            RegisterFragmentDirections.actionRegisterToWork()
        )
    }

    // Função Animação da Imagem
    private fun runEnterAnimation() = with(binding) {
        val d = resources.displayMetrics.density

        val heroDy = -72f * d
        val formDy = 36f * d

        val slowOut = android.view.animation.DecelerateInterpolator(2f)
        val slowInOut = FastOutSlowInInterpolator()

        if (imgRegister.isVisible) {
            imgRegister.alpha = 0f
            imgRegister.translationY = heroDy
            imgRegister.scaleX = 0.94f
            imgRegister.scaleY = 0.94f
            imgRegister.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(scaled(800L))
                .setStartDelay(scaled(100L))
                .setInterpolator(slowOut)
                .withLayer()
                .start()
        }

        formContainer.alpha = 0f
        formContainer.translationY = formDy
        formContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(scaled(650L))
            .setStartDelay(scaled(350L))
            .setInterpolator(slowInOut)
            .withLayer()
            .start()
    }

    // Lottie
    private fun playFinalAnimThenNavigate() = with(binding) {
        if (didNavigateAfterAnim) return@with

        registerOverlay.isVisible = true

        lottieRegister.removeAllAnimatorListeners()

        lottieRegister.repeatCount = 0
        lottieRegister.repeatMode = LottieDrawable.RESTART

        val current = lottieRegister.progress.coerceIn(0f, 1f)
        val start = if (current >= 0.95f) 0.75f else current
        try {
            lottieRegister.setMinAndMaxProgress(start, 1f)
        } catch (_: Throwable) {
            lottieRegister.setMinProgress(start)
            lottieRegister.setMaxProgress(1f)
        }

        finalAnimListener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!didNavigateAfterAnim) {
                    didNavigateAfterAnim = true

                    try {
                        lottieRegister.setMinAndMaxProgress(0f, 1f)
                    } catch (_: Throwable) {
                    }

                    registerOverlay.animate()
                        .alpha(0f)
                        .setDuration(180L)
                        .withEndAction {
                            registerOverlay.isGone = true
                            registerOverlay.alpha = 1f
                            navigateToWork()
                        }
                        .start()
                }
            }
        }
        lottieRegister.addAnimatorListener(finalAnimListener)

        if (!lottieRegister.isAnimating) {
            lottieRegister.playAnimation()
        }
    }

    companion object {
        private const val KEY_HAS_PLAYED_ENTER_ANIM = "hasPlayedEnterAnim"
        private const val KEY_PREV_HERO_VISIBLE = "prevHeroVisible"
    }
}