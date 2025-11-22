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
 *
 * Mapeamento atual de etapas:
 * 0 – Abertura
 * 1 – Tipo de Revestimento
 * 2 – Tipo de Ambiente
 * 3 – Tipo de Tráfego (apenas Piso Intertravado)
 * 4 – Medidas da Área
 * 5 – Medidas do Parâmetro
 * 6 – Revisão de Parâmetros
 * 7 – Resultado Final (Tabela)
 */
class StepNavigationHandler {

    /** Atualiza botões e layout conforme a etapa atual */
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
        // Botão Voltar: visível a partir da etapa 2
        btnBack.isVisible = step > 1
        // Botão Avançar: visível nas etapas 2..5 ; some na Revisão (6) e Resultado (7)
        btnNext.isVisible = step in 2..5
        // Botão Calcular: visível na etapa de Revisão (6); some nas demais
        btnCalcular.isVisible = (step == 6)
        // Configura botão especial "Novo Cálculo" na etapa de resultado (step 7)
        if (step == 7) {
            onSetupNovoCalculo()
        } else {
            onRestoreDefaultBack()
        }
        // Ajusta layout da tela inicial (step 0)
        adjustStep0Layout(step, bottomBar, viewFlipper)
    }

    /**  Ajusta layout da tela inicial (centraliza verticalmente) */
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