package com.luizeduardobrandao.obra.ui.calculo.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.View
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
     */
    fun smoothScrollToY(scrollableView: View, targetY: Int, baseDuration: Long = 350L) {
        scrollableView.post {
            val startY = scrollableView.scrollY
            if (startY == targetY) return@post

            val distance = abs(targetY - startY).toFloat()

            // Quanto maior a distância, maior a duração (até um limite)
            val duration = (baseDuration + distance * 0.25f)
                .toLong()
                .coerceIn(400L, 900L) // mínimo 400ms, máximo 900ms

            ValueAnimator.ofInt(startY, targetY).apply {
                this.duration = duration
                // Começa um pouco mais rápido e desacelera bem no final
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { animator ->
                    val y = animator.animatedValue as Int
                    scrollableView.scrollTo(0, y)
                }
                start()
            }
        }
    }
}