package com.luizeduardobrandao.obra.ui.calculo.ui

import android.view.View
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications

/**
 * Gerencia a visibilidade condicional de componentes da UI
 *
 * Responsável por:
 * - Mostrar/ocultar campos baseado no tipo de revestimento
 * - Gerenciar visibilidade de grupos (rodapé)
 * - Limpar campos quando ocultados
 * - Manter estado consistente da UI
 */
@Suppress("UNUSED_PARAMETER")
class VisibilityManager {

    /**
     * Atualiza visibilidade de todos os componentes baseado nos inputs
     */
    fun updateAllVisibilities(
        inputs: CalcRevestimentoViewModel.Inputs,
        // TextViews informativos
        tvAreaTotalAviso: View,
        // Grupos de componentes
        groupPlacaTipo: View, groupPecaTamanho: View, groupPastilhaTamanho: View,
        groupPastilhaPorcelanatoTamanho: View, groupRodapeFields: View,
        // Campos individuais - Medidas
        tilComp: TextInputLayout, tilLarg: TextInputLayout, tilAltura: TextInputLayout,
        tilParedeQtd: TextInputLayout, tilAbertura: TextInputLayout,
        tilAreaInformada: TextInputLayout,
        // Campos individuais - Peça
        tilPecaComp: TextInputLayout, tilPecaLarg: TextInputLayout, tilPecaEsp: TextInputLayout,
        tilJunta: TextInputLayout, tilPecasPorCaixa: TextInputLayout,
        tilDesnivel: TextInputLayout, tilSobra: TextInputLayout,
        // Campos individuais - Rodapé
        tilRodapeAltura: TextInputLayout, tilRodapeAbertura: TextInputLayout,
        tilRodapeCompComercial: TextInputLayout,
        // EditTexts (para limpar quando ocultar)
        etLarg: TextInputEditText, etAlt: TextInputEditText, etParedeQtd: TextInputEditText,
        etAbertura: TextInputEditText, etPecaEsp: TextInputEditText, etJunta: TextInputEditText,
        etPecasPorCaixa: TextInputEditText, etRodapeAbertura: TextInputEditText,
        // RadioGroups
        rgPlacaTipo: RadioGroup,
        // Switches
        switchRodape: CompoundButton
    ) {
        // Atualiza avisos informativos
        updateAreaTotalAvisoVisibility(inputs, tvAreaTotalAviso)

        // Atualiza grupos principais
        updateGroupVisibilities(
            inputs,
            groupPlacaTipo, groupPecaTamanho, groupPastilhaTamanho, groupPastilhaPorcelanatoTamanho,
            groupRodapeFields, switchRodape
        )

        // Atualiza campos de medidas
        updateMeasurementFieldsVisibility(
            inputs, tilComp, tilLarg, tilAltura, tilParedeQtd, tilAbertura,
            etLarg, etAlt, etParedeQtd, etAbertura
        )

        // Atualiza campos de peça
        updatePieceFieldsVisibility(
            inputs, tilPecaEsp, tilJunta, tilPecasPorCaixa, tilDesnivel,
            etPecaEsp, etJunta, etPecasPorCaixa
        )

        // Atualiza campos de rodapé
        updateRodapeFieldsVisibility(inputs, groupRodapeFields, etRodapeAbertura, tilRodapeAbertura)

        // Atualiza switches (apenas rodapé agora)
        updateSwitchStates(inputs, switchRodape)

        // Limpa RadioGroup de placa se necessário
        clearPlacaTipoIfNeeded(inputs, rgPlacaTipo)
    }

    /**
     * Atualiza visibilidade do aviso de área total informada
     */
    private fun updateAreaTotalAvisoVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        tvAreaTotalAviso: View
    ) {
        tvAreaTotalAviso.isVisible = inputs.areaInformadaM2 != null
    }

    /**
     * Atualiza visibilidade dos grupos principais
     */
    private fun updateGroupVisibilities(
        inputs: CalcRevestimentoViewModel.Inputs,
        groupPlacaTipo: View,
        groupPecaTamanho: View,
        groupPastilhaTamanho: View,
        groupPastilhaPorcelanatoTamanho: View,
        groupRodapeFields: View,
        switchRodape: CompoundButton
    ) {
        val revest = inputs.revest

        // Grupo de tipo de placa (cerâmica/porcelanato):
        // agora para Piso, Azulejo e Pastilha
        groupPlacaTipo.isVisible =
            revest == CalcRevestimentoViewModel.RevestimentoType.PISO ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.AZULEJO ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA

        // Grupo de tamanho tradicional (oculta para Pedra e Pastilha)
        groupPecaTamanho.isVisible =
            revest != CalcRevestimentoViewModel.RevestimentoType.PEDRA &&
                    revest != CalcRevestimentoViewModel.RevestimentoType.PASTILHA

        // Grupos de tamanho de pastilha (cerâmica x porcelanato)
        if (revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA) {
            when (inputs.pisoPlacaTipo) {
                CalcRevestimentoViewModel.PlacaTipo.PORCELANATO -> {
                    groupPastilhaTamanho.isVisible = false
                    groupPastilhaPorcelanatoTamanho.isVisible = true
                }

                CalcRevestimentoViewModel.PlacaTipo.CERAMICA, null -> {
                    // Cenário "Pastilha = Cerâmica" mantém layout já existente
                    groupPastilhaTamanho.isVisible = true
                    groupPastilhaPorcelanatoTamanho.isVisible = false
                }
            }
        } else {
            groupPastilhaTamanho.isVisible = false
            groupPastilhaPorcelanatoTamanho.isVisible = false
        }

        // Mármore/Granito em PAREDE NÃO têm etapa de rodapé
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)

        switchRodape.isVisible = hasRodapeStep
        groupRodapeFields.isVisible = hasRodapeStep && inputs.rodapeEnable
    }

    /**
     * Atualiza visibilidade dos campos de medidas (Comp/Larg/Alt/Parede/Abertura)
     */
    private fun updateMeasurementFieldsVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        tilComp: TextInputLayout,
        tilLarg: TextInputLayout,
        tilAltura: TextInputLayout,
        tilParedeQtd: TextInputLayout,
        tilAbertura: TextInputLayout,
        etLarg: TextInputEditText,
        etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText,
        etAbertura: TextInputEditText
    ) {
        val isAzulejo = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.AZULEJO
        val isPastilha = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA
        val isMG = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                inputs.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO
        val isParedeMG = isMG && inputs.aplicacao == CalcRevestimentoViewModel.AplicacaoType.PAREDE

        val isParedeMode = isAzulejo || isPastilha || isParedeMG

        // Campos de parede (altura, qtd paredes, abertura)
        tilAltura.isVisible = isParedeMode
        tilParedeQtd.isVisible = isParedeMode
        tilAbertura.isVisible = isParedeMode

        // Largura só quando NÃO é parede
        tilLarg.isVisible = !isParedeMode

        // Limpa campos ocultados
        if (!tilLarg.isVisible) {
            etLarg.text?.clear()
            tilLarg.error = null
        }
        if (!tilAltura.isVisible) {
            etAlt.text?.clear()
            tilAltura.error = null
        }
        if (!tilParedeQtd.isVisible) {
            etParedeQtd.text?.clear()
            tilParedeQtd.error = null
        }
        if (!tilAbertura.isVisible) {
            etAbertura.text?.clear()
            tilAbertura.error = null
        }
    }

    /**
     * Atualiza visibilidade dos campos de peça
     */
    private fun updatePieceFieldsVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        tilPecaEsp: TextInputLayout,
        tilJunta: TextInputLayout,
        tilPecasPorCaixa: TextInputLayout,
        tilDesnivel: TextInputLayout,
        etPecaEsp: TextInputEditText,
        etJunta: TextInputEditText,
        etPecasPorCaixa: TextInputEditText
    ) {
        val isPastilha = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA
        val isIntertravado =
            inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO
        val isMG = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                inputs.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO
        val isPedra = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA

        // Espessura: oculta para Pastilha
        tilPecaEsp.isVisible = !isPastilha
        if (isPastilha) {
            etPecaEsp.text?.clear()
            tilPecaEsp.error = null
        }

        // Junta: oculta para Piso Intertravado, visível para demais
        val showJunta = !isIntertravado
        tilJunta.isVisible = showJunta
        if (!showJunta) {
            etJunta.text?.clear()
            tilJunta.error = null
        }

        // Peças por caixa: oculta para MG, Pedra e Intertravado
        val hidePecasPorCaixa = isMG || isPedra || isIntertravado
        tilPecasPorCaixa.isVisible = !hidePecasPorCaixa
        if (hidePecasPorCaixa) {
            etPecasPorCaixa.text?.clear()
            tilPecasPorCaixa.error = null
        }

        // Desnível: visível apenas para Pedra, MG
        tilDesnivel.isVisible = inputs.revest in setOf(
            CalcRevestimentoViewModel.RevestimentoType.PEDRA,
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO
        )
        if (!tilDesnivel.isVisible) {
            tilDesnivel.error = null
        }
    }

    /**
     * Atualiza visibilidade dos campos de rodapé
     */
    private fun updateRodapeFieldsVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        groupRodapeFields: View,
        etRodapeAbertura: TextInputEditText,
        tilRodapeAbertura: TextInputLayout
    ) {
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)

        groupRodapeFields.isVisible = hasRodapeStep && inputs.rodapeEnable

        if (!inputs.rodapeEnable || !hasRodapeStep) {
            etRodapeAbertura.text?.clear()
            tilRodapeAbertura.error = null
        }
    }

    /**
     * Atualiza estado dos switches (apenas Rodapé)
     */
    private fun updateSwitchStates(
        inputs: CalcRevestimentoViewModel.Inputs,
        switchRodape: CompoundButton
    ) {
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)

        if (!hasRodapeStep) {
            // Garante que rodapé esteja sempre desligado e travado
            if (switchRodape.isChecked) {
                switchRodape.isChecked = false   // dispara listener e zera no ViewModel
            }
            switchRodape.isEnabled = false
        } else {
            switchRodape.isEnabled = true
            switchRodape.isChecked = inputs.rodapeEnable
        }
    }

    /**
     * Limpa RadioGroup de tipo de placa
     */
    private fun clearPlacaTipoIfNeeded(
        inputs: CalcRevestimentoViewModel.Inputs,
        rgPlacaTipo: RadioGroup
    ) {
        val revest = inputs.revest
        val shouldKeepPlacaTipo =
            revest == CalcRevestimentoViewModel.RevestimentoType.PISO ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.AZULEJO ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA

        if (!shouldKeepPlacaTipo || inputs.pisoPlacaTipo == null) {
            if (rgPlacaTipo.checkedRadioButtonId != View.NO_ID) {
                rgPlacaTipo.clearCheck()
            }
        }
    }
}