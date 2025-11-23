package com.luizeduardobrandao.obra.ui.calculo.ui

import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications

/** Gerencia a visibilidade condicional de componentes da UI
 *  Responsável por:
 *  - Mostrar/ocultar campos baseado no tipo de revestimento
 *  - Gerenciar visibilidade de grupos (rodapé)
 *  - Limpar campos quando ocultados
 *  - Manter estado consistente da UI */
@Suppress("UNUSED_PARAMETER")
class VisibilityManager {
    /** Atualiza visibilidade de todos os componentes baseado nos inputs */
    fun updateAllVisibilities(
        inputs: CalcRevestimentoViewModel.Inputs,
        // Grupos de componentes
        groupPlacaTipo: View, groupPecaTamanho: View, groupPastilhaTamanho: View,
        groupPastilhaPorcelanatoTamanho: View, groupRodapeFields: View, groupMgAplicacao: View,
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
        // EditTexts (para limpeza)
        etLarg: TextInputEditText, etAlt: TextInputEditText, etParedeQtd: TextInputEditText,
        etAbertura: TextInputEditText, etPecaEsp: TextInputEditText, etJunta: TextInputEditText,
        etPecasPorCaixa: TextInputEditText, etRodapeAbertura: TextInputEditText,
        // RadioGroups
        rgPlacaTipo: RadioGroup,
        // Switches
        switchRodape: CompoundButton,
        // NOVOS grupos para switches adicionais
        rowPecasPorCaixaSwitch: View, groupPecasPorCaixaFields: View,
        rowDesnivelSwitchStep4: View, groupDesnivelFields: View
    ) {
        updateGroupVisibilities(                    // Atualiza grupos principais
            inputs,
            groupPlacaTipo, groupPecaTamanho, groupPastilhaTamanho, groupPastilhaPorcelanatoTamanho,
            groupRodapeFields, groupMgAplicacao, switchRodape
        )
        updateMeasurementFieldsVisibility(          // Atualiza campos de medidas da área
            inputs, tilComp, tilLarg, tilAltura, tilParedeQtd, tilAbertura, tilAreaInformada,
            etLarg, etAlt, etParedeQtd, etAbertura
        )
        updatePieceFieldsVisibility(                // Atualiza campos de medidas do revestimento
            inputs, tilPecaEsp, tilJunta, tilPecasPorCaixa, tilDesnivel, etPecaEsp, etJunta,
            etPecasPorCaixa, rowPecasPorCaixaSwitch, rowDesnivelSwitchStep4,
            groupPecasPorCaixaFields, groupDesnivelFields
        )
        updateRodapeFieldsVisibility(               // Atualiza campos de rodapé (visibilidade + limpeza)
            inputs, groupRodapeFields,
            tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial, etRodapeAbertura
        )
        updateSwitchStates(inputs, switchRodape)    // Atualiza Switch Rodapé
        reorderDesnivelAndSobraForPedra(            // Reordena campo Desnível em Pedra Portuguesa
            inputs = inputs, tilSobra = tilSobra, groupDesnivelFields = groupDesnivelFields,
            rowDesnivelSwitchStep4 = rowDesnivelSwitchStep4
        )
        clearPlacaTipoIfNeeded(inputs, rgPlacaTipo) // Limpa RadioGroup de placa se necessário
    }

    /** Atualiza visibilidade dos grupos principais */
    private fun updateGroupVisibilities(
        inputs: CalcRevestimentoViewModel.Inputs,
        groupPlacaTipo: View, groupPecaTamanho: View, groupPastilhaTamanho: View,
        groupPastilhaPorcelanatoTamanho: View, groupRodapeFields: View, groupMgAplicacao: View,
        switchRodape: CompoundButton
    ) {
        val revest = inputs.revest
        // Grupo de tipo de placa (cerâmica/porcelanato)
        groupPlacaTipo.isVisible =
            revest == CalcRevestimentoViewModel.RevestimentoType.PISO ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.AZULEJO ||
                    revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA
        // Grupo de tamanho tradicional (oculta para Pedra e Pastilha)
        groupPecaTamanho.isVisible =
            revest != CalcRevestimentoViewModel.RevestimentoType.PEDRA &&
                    revest != CalcRevestimentoViewModel.RevestimentoType.PASTILHA
        // Grupo de aplicação (Piso/Parede) – exclusivo de Mármore/Granito
        val isMg = revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO
        groupMgAplicacao.isVisible = isMg
        // Grupos de tamanho de pastilha (cerâmica x porcelanato)
        if (revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA) {
            when (inputs.pisoPlacaTipo) {
                CalcRevestimentoViewModel.PlacaTipo.PORCELANATO -> {
                    groupPastilhaTamanho.isVisible = false
                    groupPastilhaPorcelanatoTamanho.isVisible = true
                }

                CalcRevestimentoViewModel.PlacaTipo.CERAMICA, null -> { // Cenário "Pastilha = Cerâmica"
                    groupPastilhaTamanho.isVisible = true
                    groupPastilhaPorcelanatoTamanho.isVisible = false
                }
            }
        } else {
            groupPastilhaTamanho.isVisible = false
            groupPastilhaPorcelanatoTamanho.isVisible = false
        }
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)
        // Linha inteira "Incluir rodapé" (TextView + switch)
        val rowRodapeSwitch = switchRodape.parent as? View
        rowRodapeSwitch?.isVisible = hasRodapeStep
        // Switch Rodapé visível apenas quando existe etapa de rodapé
        switchRodape.isVisible = hasRodapeStep
    }

    /** Atualiza visibilidade campos medidas da área (Comp/Larg/Alt/Parede/Abertura/Área Total) */
    private fun updateMeasurementFieldsVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        tilComp: TextInputLayout, tilLarg: TextInputLayout, tilAltura: TextInputLayout,
        tilParedeQtd: TextInputLayout, tilAbertura: TextInputLayout,
        tilAreaInformada: TextInputLayout, etLarg: TextInputEditText, etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText, etAbertura: TextInputEditText
    ) {
        val isAzulejo = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.AZULEJO
        val isPastilha = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA
        val isMG = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.MARMORE ||
                inputs.revest == CalcRevestimentoViewModel.RevestimentoType.GRANITO
        val isParedeMG = isMG && inputs.aplicacao == CalcRevestimentoViewModel.AplicacaoType.PAREDE
        val isParedeMode = isAzulejo || isPastilha || isParedeMG

        val isAreaTotalMode = inputs.areaTotalMode

        if (isAreaTotalMode) {  // == MODO ÁREA TOTAL == (Esconde dimensões: Comp/Larg/Alt/Parede)
            tilComp.isVisible = false
            tilLarg.isVisible = false
            tilAltura.isVisible = false
            tilParedeQtd.isVisible = false
            // Mostra Área Total + Abertura
            tilAreaInformada.isVisible = true
            tilAbertura.isVisible = true
        } else { // == MODO DIMENSÕES ==
            tilAreaInformada.isVisible = false
            tilAbertura.isVisible = true

            if (isParedeMode) { // Parede: Comprimento + Altura + Parede + Abertura
                tilComp.isVisible = true
                tilAltura.isVisible = true
                tilParedeQtd.isVisible = true
                tilLarg.isVisible = false
            } else { // Piso / planos: Comprimento + Largura + Abertura
                tilComp.isVisible = true
                tilAltura.isVisible = false
                tilParedeQtd.isVisible = false
                tilLarg.isVisible = true
            }
        }
        // Limpa APENAS erros de campos que ficaram ocultos (NÃO limpa valores)
        if (!tilLarg.isVisible) {
            tilLarg.error = null
        }
        if (!tilAltura.isVisible) {
            tilAltura.error = null
        }
        if (!tilParedeQtd.isVisible) {
            tilParedeQtd.error = null
        }
        if (!tilComp.isVisible) {
            tilComp.error = null
        }
        if (!tilAreaInformada.isVisible) {
            tilAreaInformada.error = null
        }
    }

    /** Atualiza visibilidade dos campos de peça */
    private fun updatePieceFieldsVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        tilPecaEsp: TextInputLayout, tilJunta: TextInputLayout,
        tilPecasPorCaixa: TextInputLayout, tilDesnivel: TextInputLayout,
        etPecaEsp: TextInputEditText, etJunta: TextInputEditText,
        etPecasPorCaixa: TextInputEditText,
        rowPecasPorCaixaSwitch: View, groupPecasPorCaixaFields: View,
        rowDesnivelSwitchStep4: View, groupDesnivelFields: View
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
        if (hidePecasPorCaixa) {
            rowPecasPorCaixaSwitch.isVisible = false
            groupPecasPorCaixaFields.isVisible = false
            tilPecasPorCaixa.isVisible = false
            etPecasPorCaixa.text?.clear()
            tilPecasPorCaixa.error = null
        } else {
            rowPecasPorCaixaSwitch.isVisible = true
            tilPecasPorCaixa.isVisible = true
        }

        val showDesnivel = inputs.revest in setOf(
            CalcRevestimentoViewModel.RevestimentoType.PEDRA,
            CalcRevestimentoViewModel.RevestimentoType.MARMORE,
            CalcRevestimentoViewModel.RevestimentoType.GRANITO
        )
        tilDesnivel.isVisible = showDesnivel

        when {
            isPedra -> { // Pedra Portuguesa: sem switch, campo sempre visível
                rowDesnivelSwitchStep4.isVisible = false
                groupDesnivelFields.isVisible = showDesnivel
            }

            isMG -> {    // MG: switch visível, grupo controlado pelo switch no Fragment
                rowDesnivelSwitchStep4.isVisible = showDesnivel
            }

            else -> {
                rowDesnivelSwitchStep4.isVisible = false
                groupDesnivelFields.isVisible = false
            }
        }

        if (!showDesnivel) {
            tilDesnivel.error = null
        }
    }

    /** Atualiza visibilidade dos campos de rodapé */
    private fun updateRodapeFieldsVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        groupRodapeFields: View, tilRodapeAltura: TextInputLayout,
        tilRodapeAbertura: TextInputLayout, tilRodapeCompComercial: TextInputLayout,
        etRodapeAbertura: TextInputEditText
    ) {
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)
        val rodapeOn = hasRodapeStep && inputs.rodapeEnable
        groupRodapeFields.isVisible = rodapeOn

        if (!rodapeOn) { // Limpa valores e erros de TODOS os campos de rodapé
            (tilRodapeAltura.editText as? TextInputEditText)?.text?.clear()
            etRodapeAbertura.text?.clear()
            (tilRodapeCompComercial.editText as? TextInputEditText)?.text?.clear()
            tilRodapeAltura.error = null
            tilRodapeAbertura.error = null
            tilRodapeCompComercial.error = null
        }
    }

    /** Atualiza estado do Switch Rodapé */
    private fun updateSwitchStates(
        inputs: CalcRevestimentoViewModel.Inputs, switchRodape: CompoundButton
    ) {
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)
        if (!hasRodapeStep) {
            if (switchRodape.isChecked) {
                switchRodape.isChecked = false
            }
            switchRodape.isEnabled = false
        } else {
            switchRodape.isEnabled = true
            switchRodape.isChecked = inputs.rodapeEnable
        }
    }

    /** Limpa RadioGroup de tipo de placa */
    private fun clearPlacaTipoIfNeeded(
        inputs: CalcRevestimentoViewModel.Inputs, rgPlacaTipo: RadioGroup
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

    /** Somente Para Pedra Portuguesa → sem switch de desnível ; Campo Desnível ACIMA de "Sobra" */
    private fun reorderDesnivelAndSobraForPedra(
        inputs: CalcRevestimentoViewModel.Inputs,
        tilSobra: TextInputLayout, groupDesnivelFields: View, rowDesnivelSwitchStep4: View
    ) {
        val parent = tilSobra.parent as? ViewGroup ?: return
        val isPedra =
            inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PEDRA
        if (parent.indexOfChild(groupDesnivelFields) == -1) return

        if (isPedra && groupDesnivelFields.isVisible && !rowDesnivelSwitchStep4.isVisible) {
            val sobraIndex = parent.indexOfChild(tilSobra)
            val desnivelIndex = parent.indexOfChild(groupDesnivelFields)
            if (sobraIndex == -1 || desnivelIndex == -1) return
            if (desnivelIndex > sobraIndex) {
                parent.removeView(groupDesnivelFields)
                val newSobraIndex = parent.indexOfChild(tilSobra)
                if (newSobraIndex == -1) {
                    parent.addView(groupDesnivelFields)
                    return
                }
                val targetIndex = newSobraIndex.coerceIn(0, parent.childCount)
                parent.addView(groupDesnivelFields, targetIndex)
            }
        } else {
            val rowIndexBefore = (rowDesnivelSwitchStep4.parent as? ViewGroup)
                ?.indexOfChild(rowDesnivelSwitchStep4) ?: -1
            if (rowIndexBefore == -1 || rowDesnivelSwitchStep4.parent != parent) {
                return
            }
            val groupIndexBefore = parent.indexOfChild(groupDesnivelFields)
            if (groupIndexBefore == rowIndexBefore + 1) return

            parent.removeView(groupDesnivelFields)

            val rowIndexAfter = parent.indexOfChild(rowDesnivelSwitchStep4)
            if (rowIndexAfter == -1) {
                parent.addView(groupDesnivelFields)
                return
            }
            val targetIndex = (rowIndexAfter + 1).coerceIn(0, parent.childCount)
            parent.addView(groupDesnivelFields, targetIndex)
        }
    }
}