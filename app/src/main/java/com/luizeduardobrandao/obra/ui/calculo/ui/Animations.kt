package com.luizeduardobrandao.obra.ui.calculo.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Controla as animações da Tela 7 (Revisão de Parâmetros).
 *
 * Ordem:
 * 1) Card aparece
 * 2) Conteúdo interno (texto) aparece
 * 3) Botão "Calcular" aparece
 */
object Animations {

    // Mantém referência fraca para não vazar View/Fragment
    private val scrollAnimators = WeakHashMap<View, ValueAnimator>()

    // Animação Card Revisão de Parâmetros
    fun playReviewAnimation(card: View, content: View, button: View) {
        // Evita animar se a view ainda não está na hierarquia
        if (card.width == 0 && card.height == 0) {
            card.post { playReviewAnimation(card, content, button) }
            return
        }

        val density = card.resources.displayMetrics.density
        val smallOffset = 8f * density
        val largeOffset = 16f * density

        // Estados iniciais
        card.alpha = 0f
        card.translationY = largeOffset
        content.alpha = 0f
        content.translationY = smallOffset
        button.alpha = 0f
        button.translationY = largeOffset

        // Interpolador base para todas as animações
        val animInterpolator = AccelerateDecelerateInterpolator()

        // 1) Card
        val cardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, largeOffset, 0f)
            )
            duration = 220L
            interpolator = animInterpolator
        }
        // 2) Conteúdo
        val contentAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(content, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(content, View.TRANSLATION_Y, smallOffset, 0f)
            )
            duration = 220L
            interpolator = animInterpolator
        }
        // 3) Botão
        val buttonAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(button, View.TRANSLATION_Y, largeOffset, 0f)
            )
            duration = 220L
            interpolator = animInterpolator
        }

        AnimatorSet().apply {
            playSequentially(cardAnim, contentAnim, buttonAnim)
            start()
        }
    }

    /**
     * Rolagem vertical realmente suave.
     * - Duração aumenta conforme a distância percorrida.
     * - Usa interpolador desacelerando no final (sem tranco).
     * - Cancelável por view (para não brigar com trocas de etapa).
     */
    fun smoothScrollToY(scrollableView: View, targetY: Int, baseDuration: Long = 350L) {
        scrollableView.post {
            // Cancela animação anterior dessa mesma view, se existir
            scrollAnimators[scrollableView]?.cancel()

            val startY = scrollableView.scrollY
            if (startY == targetY) {
                scrollAnimators.remove(scrollableView)
                return@post
            }

            val distance = abs(targetY - startY).toFloat()

            // Quanto maior a distância, maior a duração (até um limite)
            val duration = (baseDuration + distance * 0.25f)
                .toLong()
                .coerceIn(400L, 900L) // mínimo 400ms, máximo 900ms

            val animator = ValueAnimator.ofInt(startY, targetY).apply {
                this.duration = duration
                // Começa um pouco mais rápido e desacelera bem no final
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { anim ->
                    val y = anim.animatedValue as Int
                    scrollableView.scrollTo(0, y)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        scrollAnimators.remove(scrollableView)
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        scrollAnimators.remove(scrollableView)
                    }
                })
            }

            scrollAnimators[scrollableView] = animator
            animator.start()
        }
    }

    /** Cancela animação de scroll em andamento para a view informada. */
    private fun cancelScrollForView(scrollableView: View) {
        scrollAnimators[scrollableView]?.cancel()
        scrollAnimators.remove(scrollableView)
    }

    /**
     * Garante que a ScrollView volte para o topo sem flicker,
     * cancelando qualquer animação em curso e usando OnPreDraw
     * para reforçar o scrollTo(0, 0) após o layout.
     *
     * - scrollView: sua ScrollView (ex.: binding.scrollContent)
     * - rootView: view raiz do layout (ex.: binding.rootCalc)
     * - expectedStep: step para o qual o layout acabou de mudar
     * - shouldHijackFocus: se true, força foco no root para evitar foco em field
     * - currentStepProvider: lambda que devolve o step atual (ex.: { viewModel.step.value })
     */
    fun ensureScrollAtTopWithoutFlicker(
        scrollView: View,
        rootView: View,
        expectedStep: Int,
        shouldHijackFocus: Boolean,
        currentStepProvider: () -> Int
    ) {
        // 1) Cancela QUALQUER animação de scroll que ainda esteja rodando
        cancelScrollForView(scrollView)

        val prevFocusable = rootView.isFocusableInTouchMode

        if (shouldHijackFocus) {
            rootView.isFocusableInTouchMode = true
            rootView.requestFocus()
        }

        // 2) Garante topo imediatamente
        scrollView.scrollTo(0, 0)

        scrollView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val vto = scrollView.viewTreeObserver
                if (vto.isAlive) vto.removeOnPreDrawListener(this)

                // Só reforça o topo se ainda estivermos no mesmo step
                if (currentStepProvider() == expectedStep) {
                    scrollView.scrollTo(0, 0)
                }

                rootView.isFocusableInTouchMode = prevFocusable
                return true
            }
        })
    }
}