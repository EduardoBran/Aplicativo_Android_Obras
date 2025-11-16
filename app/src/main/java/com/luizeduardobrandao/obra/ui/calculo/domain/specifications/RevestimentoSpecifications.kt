package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules

/**
 * Especificações técnicas de revestimentos (espessura, junta, formatos)
 */
object RevestimentoSpecifications {

    // Atalhos para regras numéricas
    private val PecaRules = CalcRevestimentoRules.Peca
    private val MGRules = CalcRevestimentoRules.MarmoreGranito
    private val PisoRules = CalcRevestimentoRules.Piso
    private val PedraRules = CalcRevestimentoRules.Pedra
    private val JuntaPadrao = CalcRevestimentoRules.JuntaPadrao
    private val InterRules = CalcRevestimentoRules.Intertravado

    // Formatos suportados para pastilha
    enum class PastilhaFormato(
        val ladoCm: Double,
        val mantaLadoCm: Double,
        val espMmPadrao: Double
    ) {
        P5(5.0, 32.5, PecaRules.PASTILHA_ESP_P5_MM),
        P7_5(7.5, 31.5, PecaRules.PASTILHA_ESP_P7_5_MM),
        P10(10.0, 31.0, PecaRules.PASTILHA_ESP_P10_MM)
    }

    /**
     * Retorna espessura padrão em mm conforme tipo de revestimento
     */
    fun getEspessuraPadraoMm(inputs: Inputs): Double {
        return when (inputs.revest) {
            RevestimentoType.PASTILHA -> when (inputs.pastilhaFormato) {
                PastilhaFormato.P5 -> PecaRules.PASTILHA_ESP_P5_MM
                PastilhaFormato.P7_5 -> PecaRules.PASTILHA_ESP_P7_5_MM
                PastilhaFormato.P10 -> PecaRules.PASTILHA_ESP_P10_MM
                null -> PecaRules.PASTILHA_ESP_P5_MM
            }

            RevestimentoType.PEDRA ->
                PedraRules.ESP_PADRAO_MM

            RevestimentoType.PISO_INTERTRAVADO ->
                InterRules.ESP_PADRAO_MM

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> {
                val amb = inputs.ambiente
                val aplic = inputs.aplicacao

                when {
                    // Fallback neutro enquanto ainda não escolheu tudo
                    amb == null || aplic == null -> MGRules.ESP_FALLBACK_MM

                    // Piso
                    aplic == AplicacaoType.PISO &&
                            (amb == AmbienteType.SECO || amb == AmbienteType.SEMI) ->
                        MGRules.ESP_PISO_SECO_SEMI_MM

                    aplic == AplicacaoType.PISO && amb == AmbienteType.MOLHADO ->
                        MGRules.ESP_PISO_MOLHADO_MM

                    aplic == AplicacaoType.PISO && amb == AmbienteType.SEMPRE ->
                        MGRules.ESP_PISO_SEMPRE_MM

                    // Parede
                    aplic == AplicacaoType.PAREDE &&
                            (amb == AmbienteType.SECO || amb == AmbienteType.SEMI) ->
                        MGRules.ESP_PAREDE_SECO_SEMI_MM

                    aplic == AplicacaoType.PAREDE && amb == AmbienteType.MOLHADO ->
                        MGRules.ESP_PAREDE_MOLHADO_MM

                    aplic == AplicacaoType.PAREDE && amb == AmbienteType.SEMPRE ->
                        MGRules.ESP_PAREDE_SEMPRE_MM

                    else -> MGRules.ESP_FALLBACK_MM
                }
            }

            RevestimentoType.PISO -> {
                if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                    val maxLado =
                        kotlin.math.max(inputs.pecaCompCm ?: 0.0, inputs.pecaLargCm ?: 0.0)
                    when {
                        maxLado >= PisoRules.PORCELANATO_LADO_GRANDE_CM ->
                            PisoRules.PORCELANATO_ESP_GRANDE_MM

                        maxLado >= PisoRules.PORCELANATO_LADO_MEDIO_CM ->
                            PisoRules.PORCELANATO_ESP_DEFAULT_MM

                        else ->
                            PisoRules.PORCELANATO_ESP_DEFAULT_MM
                    }
                } else {
                    PisoRules.CERAMICO_ESP_DEFAULT_MM
                }
            }

            else -> PisoRules.ESP_DEFAULT_OUTROS_MM
        }
    }

    /**
     * Retorna junta padrão em mm conforme tipo de revestimento
     */
    fun getJuntaPadraoMm(inputs: Inputs): Double {
        return when (inputs.revest) {
            RevestimentoType.PASTILHA -> JuntaPadrao.PASTILHA_MM
            RevestimentoType.PEDRA -> JuntaPadrao.PEDRA_MM

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> JuntaPadrao.MG_MM

            RevestimentoType.PISO_INTERTRAVADO -> JuntaPadrao.INTERTRAVADO_MM

            RevestimentoType.PISO -> {
                if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO)
                    JuntaPadrao.PISO_PORCELANATO_MM
                else
                    JuntaPadrao.PISO_CERAMICO_MM
            }

            RevestimentoType.AZULEJO -> JuntaPadrao.AZULEJO_MM
            else -> JuntaPadrao.GENERICO_MM
        }
    }

    /**
     * Tipos de revestimento que suportam rodapé (tratamento especial para Mármore e Granito)
     */
    fun hasRodapeStep(inputs: Inputs): Boolean {
        return when (inputs.revest) {
            RevestimentoType.PISO -> true

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO ->
                inputs.aplicacao == AplicacaoType.PISO

            else -> false
        }
    }

    /**
     * Verifica se é pedra ou similares (mármore/granito)
     */
    fun isPedraOuSimilares(revest: RevestimentoType?) = revest in setOf(
        RevestimentoType.PEDRA,
        RevestimentoType.MARMORE,
        RevestimentoType.GRANITO
    )

    /**
     * Gera sufixo de tamanho para o nome do item
     */
    fun tamanhoSufixo(inputs: Inputs): String {
        val (c, l) = inputs.pecaCompCm to inputs.pecaLargCm
        return if (c != null && l != null) " ${arred0(c)}×${arred0(l)} cm" else ""
    }

    // Funções de arredondamento
    private fun arred0(v: Double) = kotlin.math.round(v)
}