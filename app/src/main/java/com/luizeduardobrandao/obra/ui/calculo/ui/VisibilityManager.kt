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
 * - Gerenciar visibilidade de grupos (rodapé, impermeabilização)
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
        groupRodapeFields: View, groupIntertravadoImpOptions: View,
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
        rgPlacaTipo: RadioGroup, rgIntertravadoImp: RadioGroup,
        // Switches
        switchImp: CompoundButton, switchRodape: CompoundButton
    ) {
        // Atualiza avisos informativos
        updateAreaTotalAvisoVisibility(inputs, tvAreaTotalAviso)

        // Atualiza grupos principais
        updateGroupVisibilities(
            inputs, groupPlacaTipo, groupPecaTamanho, groupPastilhaTamanho,
            groupRodapeFields, groupIntertravadoImpOptions, rgIntertravadoImp,
            switchImp, switchRodape
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

        // Atualiza switches
        updateSwitchStates(inputs, switchImp, switchRodape)

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
        groupRodapeFields: View,
        groupIntertravadoImpOptions: View,
        rgIntertravadoImp: RadioGroup,
        switchImp: CompoundButton,
        switchRodape: CompoundButton
    ) {
        // Grupo de tipo de placa (cerâmica/porcelanato)
        groupPlacaTipo.isVisible = inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PISO

        // Grupo de tamanho tradicional (oculta para Pedra e Pastilha)
        groupPecaTamanho.isVisible =
            inputs.revest != CalcRevestimentoViewModel.RevestimentoType.PEDRA &&
                    inputs.revest != CalcRevestimentoViewModel.RevestimentoType.PASTILHA

        // Grupo de tamanho de pastilha (só visível para Pastilha)
        groupPastilhaTamanho.isVisible =
            inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PASTILHA

        // Mármore/Granito em PAREDE NÃO têm etapa de rodapé
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)

        switchRodape.isVisible = hasRodapeStep
        groupRodapeFields.isVisible = hasRodapeStep && inputs.rodapeEnable

        // Impermeabilização para Piso Intertravado
        updateIntertravadoImpVisibility(
            inputs, switchImp, groupIntertravadoImpOptions, rgIntertravadoImp
        )
    }

    /**
     * Atualiza visibilidade de impermeabilização para Piso Intertravado
     */
    private fun updateIntertravadoImpVisibility(
        inputs: CalcRevestimentoViewModel.Inputs,
        switchImp: CompoundButton, // ✅ CORRIGIDO
        groupIntertravadoImpOptions: View,
        rgIntertravadoImp: RadioGroup
    ) {
        if (inputs.revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
            val amb = inputs.ambiente
            val traf = inputs.trafego

            // Se ainda não tem ambiente ou tráfego, ou se for SECO → sem opções
            if (amb == null || traf == null || amb == CalcRevestimentoViewModel.AmbienteType.SECO) {
                switchImp.isVisible = false
                groupIntertravadoImpOptions.isVisible = false
                rgIntertravadoImp.clearCheck()
            } else {
                // Sempre mostrar switch quando houver etapa
                switchImp.isVisible = true
                switchImp.isEnabled = true

                // Mostra opções de tipo apenas para MOLHADO/SEMPRE + LEVE/MEDIO
                val precisaEscolhaTipo =
                    (amb == CalcRevestimentoViewModel.AmbienteType.MOLHADO ||
                            amb == CalcRevestimentoViewModel.AmbienteType.SEMPRE) &&
                            (traf == CalcRevestimentoViewModel.TrafegoType.LEVE ||
                                    traf == CalcRevestimentoViewModel.TrafegoType.MEDIO)

                val showRadios = precisaEscolhaTipo && inputs.impermeabilizacaoOn
                groupIntertravadoImpOptions.isVisible = showRadios
                if (!showRadios) {
                    rgIntertravadoImp.clearCheck()
                }
            }
        } else {
            // Demais revestimentos: comportamento padrão
            switchImp.isVisible = true
            switchImp.isEnabled = !inputs.impermeabilizacaoLocked
            groupIntertravadoImpOptions.isVisible = false
        }
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

        // Junta: oculta para Piso Intertravado, visível para demais (se grupo peça visível)
        if (isIntertravado) {
            tilJunta.isVisible = false
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
     * Atualiza estado dos switches
     */
    private fun updateSwitchStates(
        inputs: CalcRevestimentoViewModel.Inputs,
        switchImp: CompoundButton,
        switchRodape: CompoundButton
    ) {
        switchImp.isEnabled = !inputs.impermeabilizacaoLocked
        switchImp.isChecked = inputs.impermeabilizacaoOn

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
     * Limpa RadioGroup de tipo de placa se não for Piso
     */
    private fun clearPlacaTipoIfNeeded(
        inputs: CalcRevestimentoViewModel.Inputs,
        rgPlacaTipo: RadioGroup
    ) {
        if (inputs.revest != CalcRevestimentoViewModel.RevestimentoType.PISO ||
            inputs.pisoPlacaTipo == null
        ) {
            if (rgPlacaTipo.checkedRadioButtonId != View.NO_ID) {
                rgPlacaTipo.clearCheck()
            }
        }
    }
}