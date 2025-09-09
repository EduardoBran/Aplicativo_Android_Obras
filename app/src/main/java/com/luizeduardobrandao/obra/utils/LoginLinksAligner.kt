package com.luizeduardobrandao.obra.utils

import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButton
import com.luizeduardobrandao.obra.databinding.FragmentLoginBinding

object LoginLinksAligner {

    /**
     * Aplica correções de alinhamento **somente em landscape**.
     * 1) Centraliza o MaterialButton dentro do FrameLayout
     * 2) Remove folgas laterais e padroniza iconPadding
     * 3) Equaliza a largura visual: ambos os botões passam a ter a largura do maior
     */
    fun applyLandscapeLinkFixes(binding: FragmentLoginBinding) {
        if (binding.root.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return

        // Passo 1/2: ajustes que afetam a medida final
        centerButtonInsideFrame(binding.tvCreateAccount)
        centerButtonInsideFrame(binding.tvForgotPassword)

        // Passo 3: equalizar larguras após o layout calcular medidas reais
        binding.linksRow.doOnLayout {
            equalizeButtonWidths(binding.tvCreateAccount, binding.tvForgotPassword)
        }
    }

    private fun centerButtonInsideFrame(btn: MaterialButton) {
        // Centraliza o filho dentro do FrameLayout
        (btn.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.gravity = Gravity.CENTER
            btn.layoutParams = lp
        }

        // Remove folgas laterais do próprio MaterialButton
        btn.minWidth = 0
        btn.setPaddingRelative(0, btn.paddingTop, 0, btn.paddingBottom)

        // Garante iconPadding simétrico
        btn.iconPadding = dpToPx(btn, 6)
    }

    /** Faz os dois botões terem a MESMA largura (a do maior deles). */
    private fun equalizeButtonWidths(left: MaterialButton, right: MaterialButton) {
        // Meça as larguras atuais (já calculadas pelo layout)
        val maxWidth = maxOf(left.measuredWidth, right.measuredWidth)

        // Atribui a mesma largura para ambos
        setExactWidth(left, maxWidth)
        setExactWidth(right, maxWidth)
    }

    private fun setExactWidth(view: View, widthPx: Int) {
        val lp = view.layoutParams
        if (lp.width != widthPx) {
            lp.width = widthPx
            view.layoutParams = lp
            // Opcional: force um novo re-layout do container
            (view.parent as? View)?.requestLayout()
        }
    }

    private fun dpToPx(view: View, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            view.resources.displayMetrics
        ).toInt()
    }

    fun applyPortraitMirrorRightGapAsLeftMargin(binding: FragmentLoginBinding) {
        val isPortrait =
            binding.root.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (!isPortrait) return

        binding.linksRow.doOnLayout {
            val container = binding.linksRow
            val leftBtn = binding.tvCreateAccount
            val rightBtn = binding.tvForgotPassword

            // utilidades locais
            fun View.xOnScreen(): Int {
                val out = IntArray(2); getLocationOnScreen(out); return out[0]
            }

            fun View.rightOnScreen(): Int = xOnScreen() + width

            // 1) gap da direita em PX (borda dir do container até o fim do botão direito)
            val gapRightPx = container.rightOnScreen() - rightBtn.rightOnScreen()

            // 2) margem desejada para o botão esquerdo = mesmo gap
            var desiredStart = gapRightPx

            // 3) clamp: não pode exceder o espaço útil da coluna esquerda
            val leftCol = leftBtn.parent as View
            val maxStart = (leftCol.width - leftBtn.width).coerceAtLeast(0)
            if (desiredStart > maxStart) desiredStart = maxStart

            // 4) aplica marginStart no botão esquerdo (em portrait o botão está alinhado a START)
            (leftBtn.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.marginStart != desiredStart) {
                    lp.marginStart = desiredStart
                    leftBtn.layoutParams = lp
                    leftCol.requestLayout()
                }
            }
        }
    }
}