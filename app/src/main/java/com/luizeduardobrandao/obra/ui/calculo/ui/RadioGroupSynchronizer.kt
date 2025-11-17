package com.luizeduardobrandao.obra.ui.calculo.ui

import android.view.View
import android.widget.RadioGroup
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.ImpermeabilizacaoSpecifications
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications

/**
 * Gerencia a sincronização de RadioGroups com o estado do ViewModel
 *
 * Responsável por:
 * - Sincronizar RadioGroups sem disparar listeners
 * - Evitar loops infinitos de atualização
 * - Manter estado consistente entre UI e ViewModel
 */
class RadioGroupSynchronizer {

    /**
     * Marca ou desmarca um RadioGroup de forma segura
     * Evita disparar o listener se o estado já está correto
     */
    private fun RadioGroup.setCheckedSafely(id: Int?) {
        if (id == null) {
            if (checkedRadioButtonId != View.NO_ID) {
                clearCheck()
            }
        } else if (checkedRadioButtonId != id) {
            check(id)
        }
    }

    /**
     * Sincroniza todos os RadioGroups com o estado atual dos Inputs
     *
     * @param inputs Estado atual do ViewModel
     * @param rgRevest RadioGroup de tipo de revestimento
     * @param rgPlacaTipo RadioGroup de tipo de placa (cerâmica/porcelanato)
     * @param rgAmbiente RadioGroup de tipo de ambiente
     * @param rgRodapeMat RadioGroup de material de rodapé
     * @param rgTrafego RadioGroup de tipo de tráfego
     * @param rgIntertravadoImp RadioGroup de impermeabilização (intertravado)
     * @param rgPastilhaTamanho RadioGroup de tamanho de pastilha
     */
    fun syncAllRadioGroups(
        inputs: CalcRevestimentoViewModel.Inputs,
        rgRevest: RadioGroup,
        rgPlacaTipo: RadioGroup,
        rgAmbiente: RadioGroup,
        rgRodapeMat: RadioGroup,
        rgTrafego: RadioGroup,
        rgIntertravadoImp: RadioGroup,
        rgPastilhaTamanho: RadioGroup,
        rgPastilhaPorcelanatoTamanho: RadioGroup
    ) {
        // Sincroniza tipo de revestimento
        syncRevestimento(inputs.revest, rgRevest)

        // Sincroniza tipo de placa (cerâmica/porcelanato)
        syncPlacaTipo(inputs.pisoPlacaTipo, rgPlacaTipo)

        // Sincroniza tipo de ambiente
        syncAmbiente(inputs.ambiente, rgAmbiente)

        // Sincroniza material de rodapé
        syncRodapeMaterial(inputs.rodapeMaterial, rgRodapeMat)

        // Sincroniza tipo de tráfego
        syncTrafego(inputs.trafego, rgTrafego)

        // Sincroniza impermeabilização (intertravado)
        syncImpermeabilizacao(inputs.impIntertravadoTipo, rgIntertravadoImp)

        // Sincroniza tamanho de pastilha (cerâmica x porcelanato)
        syncPastilhaTamanho(inputs.pastilhaFormato, rgPastilhaTamanho, rgPastilhaPorcelanatoTamanho)
    }

    /**
     * Sincroniza RadioGroup de tipo de revestimento
     */
    private fun syncRevestimento(
        revest: CalcRevestimentoViewModel.RevestimentoType?,
        rgRevest: RadioGroup
    ) {
        val radioId = when (revest) {
            CalcRevestimentoViewModel.RevestimentoType.PISO -> R.id.rbPiso
            CalcRevestimentoViewModel.RevestimentoType.AZULEJO -> R.id.rbAzulejo
            CalcRevestimentoViewModel.RevestimentoType.PASTILHA -> R.id.rbPastilha
            CalcRevestimentoViewModel.RevestimentoType.PEDRA -> R.id.rbPedra
            CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO -> R.id.rbPisoIntertravado
            CalcRevestimentoViewModel.RevestimentoType.MARMORE -> R.id.rbMarmore
            CalcRevestimentoViewModel.RevestimentoType.GRANITO -> R.id.rbGranito
            null -> null
        }
        rgRevest.setCheckedSafely(radioId)
    }

    /**
     * Sincroniza RadioGroup de tipo de placa (cerâmica/porcelanato)
     */
    private fun syncPlacaTipo(
        placaTipo: CalcRevestimentoViewModel.PlacaTipo?,
        rgPlacaTipo: RadioGroup
    ) {
        val radioId = when (placaTipo) {
            CalcRevestimentoViewModel.PlacaTipo.CERAMICA -> R.id.rbCeramica
            CalcRevestimentoViewModel.PlacaTipo.PORCELANATO -> R.id.rbPorcelanato
            null -> null
        }
        rgPlacaTipo.setCheckedSafely(radioId)
    }

    /**
     * Sincroniza RadioGroup de tipo de ambiente
     */
    private fun syncAmbiente(
        ambiente: CalcRevestimentoViewModel.AmbienteType?,
        rgAmbiente: RadioGroup
    ) {
        val radioId = when (ambiente) {
            CalcRevestimentoViewModel.AmbienteType.SECO -> R.id.rbSeco
            CalcRevestimentoViewModel.AmbienteType.SEMI -> R.id.rbSemi
            CalcRevestimentoViewModel.AmbienteType.MOLHADO -> R.id.rbMolhado
            CalcRevestimentoViewModel.AmbienteType.SEMPRE -> R.id.rbSempre
            null -> null
        }
        rgAmbiente.setCheckedSafely(radioId)
    }

    /**
     * Sincroniza RadioGroup de material de rodapé
     */
    private fun syncRodapeMaterial(
        material: CalcRevestimentoViewModel.RodapeMaterial,
        rgRodapeMat: RadioGroup
    ) {
        val radioId = when (material) {
            CalcRevestimentoViewModel.RodapeMaterial.MESMA_PECA -> R.id.rbRodapeMesma
            CalcRevestimentoViewModel.RodapeMaterial.PECA_PRONTA -> R.id.rbRodapePeca
        }
        rgRodapeMat.setCheckedSafely(radioId)
    }

    /**
     * Sincroniza RadioGroup de tipo de tráfego
     */
    private fun syncTrafego(
        trafego: CalcRevestimentoViewModel.TrafegoType?,
        rgTrafego: RadioGroup
    ) {
        val radioId = when (trafego) {
            CalcRevestimentoViewModel.TrafegoType.LEVE -> R.id.rbTrafegoLeve
            CalcRevestimentoViewModel.TrafegoType.MEDIO -> R.id.rbTrafegoMedio
            CalcRevestimentoViewModel.TrafegoType.PESADO -> R.id.rbTrafegoPesado
            null -> null
        }
        rgTrafego.setCheckedSafely(radioId)
    }

    /**
     * Sincroniza RadioGroup de tamanho de pastilha
     */
    private fun syncPastilhaTamanho(
        formato: RevestimentoSpecifications.PastilhaFormato?,
        rgPastilhaTamanho: RadioGroup,
        rgPastilhaPorcelanatoTamanho: RadioGroup
    ) {
        val (ceramicaId, porcelanatoId) = when (formato) {
            RevestimentoSpecifications.PastilhaFormato.P5 -> R.id.rbPastilha5 to null
            RevestimentoSpecifications.PastilhaFormato.P7_5 -> R.id.rbPastilha7_5 to null
            RevestimentoSpecifications.PastilhaFormato.P10 -> R.id.rbPastilha10 to null

            // Formatos específicos para Pastilha Porcelanato
            RevestimentoSpecifications.PastilhaFormato.P1_5 -> null to R.id.rbPastilhaP1_5
            RevestimentoSpecifications.PastilhaFormato.P2 -> null to R.id.rbPastilhaP2
            RevestimentoSpecifications.PastilhaFormato.P2_2 -> null to R.id.rbPastilhaP2_2
            RevestimentoSpecifications.PastilhaFormato.P2_5 -> null to R.id.rbPastilhaP2_5
            RevestimentoSpecifications.PastilhaFormato.P5_5 -> null to R.id.rbPastilhaP5_5
            RevestimentoSpecifications.PastilhaFormato.P5_10 -> null to R.id.rbPastilhaP5_10
            RevestimentoSpecifications.PastilhaFormato.P5_15 -> null to R.id.rbPastilhaP5_15
            RevestimentoSpecifications.PastilhaFormato.P7_5P -> null to R.id.rbPastilhaP7_5p
            RevestimentoSpecifications.PastilhaFormato.P10P -> null to R.id.rbPastilhaP10p

            null -> null to null
        }

        rgPastilhaTamanho.setCheckedSafely(ceramicaId)
        rgPastilhaPorcelanatoTamanho.setCheckedSafely(porcelanatoId)
    }

    /**
     * Sincroniza RadioGroup de impermeabilização (piso intertravado)
     */
    private fun syncImpermeabilizacao(
        tipo: ImpermeabilizacaoSpecifications.ImpIntertravadoTipo?,
        rgIntertravadoImp: RadioGroup
    ) {
        val radioId = when (tipo) {
            ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.MANTA_GEOTEXTIL ->
                R.id.rbImpMantaGeotextil

            ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.ADITIVO_SIKA1 ->
                R.id.rbImpAditivoSika1

            ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.MANTA_ASFALTICA,
            null -> null // Manta asfáltica não tem radio button específico
        }
        rgIntertravadoImp.setCheckedSafely(radioId)
    }
}