package com.luizeduardobrandao.obra.ui.calculo.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ViewFlipper
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton

/**
 * Gerencia a navegação entre etapas do wizard
 *
 * Responsável por:
 * - Configurar botões de navegação (Voltar/Avançar/Calcular)
 * - Ajustar layout conforme etapa
 * - Gerenciar visibilidade de menus
 * - Configurar botão "Novo Cálculo"
 */
class StepNavigationHandler {

    /**
     * Atualiza botões e layout conforme a etapa atual
     */
    fun handleStepNavigation(
        step: Int,
        btnBack: MaterialButton,
        btnNext: MaterialButton,
        btnCalcular: MaterialButton,
        bottomBar: View,
        viewFlipper: ViewFlipper,
        onSetupNovoCalculo: () -> Unit,
        onRestoreDefaultBack: () -> Unit
    ) {
        // Configura botões
        btnBack.isVisible = step > 0
        btnNext.isVisible = step in 1..7
        btnCalcular.isVisible = (step == 8)

        // Configura botão especial "Novo Cálculo" na etapa 9
        if (step == 9) {
            onSetupNovoCalculo()
        } else {
            onRestoreDefaultBack()
        }

        // Ajusta layout da tela inicial (step 0)
        adjustStep0Layout(step, bottomBar, viewFlipper)
    }

    /**
     * Ajusta layout da tela inicial (centraliza verticalmente)
     */
    private fun adjustStep0Layout(
        step: Int,
        bottomBar: View,
        viewFlipper: ViewFlipper
    ) {
        val tela0 = viewFlipper.getChildAt(0) as LinearLayout

        if (step == 0) {
            bottomBar.isGone = true
            tela0.gravity = Gravity.CENTER
        } else {
            bottomBar.isVisible = true
            tela0.gravity = Gravity.CENTER_HORIZONTAL
            tela0.minimumHeight = 0
        }
    }
}