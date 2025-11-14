package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*

/**
 * Especificações técnicas de revestimentos (espessura, junta, formatos)
 */
object RevestimentoSpecifications {

    // Formatos suportados para pastilha
    enum class PastilhaFormato(
        val ladoCm: Double,
        val mantaLadoCm: Double,
        val espMmPadrao: Double
    ) {
        P5(5.0, 32.5, 5.0),
        P7_5(7.5, 31.5, 6.0),
        P10(10.0, 31.0, 6.0)
    }

    /**
     * Retorna espessura padrão em mm conforme tipo de revestimento
     */
    fun getEspessuraPadraoMm(inputs: Inputs): Double {
        return when (inputs.revest) {
            RevestimentoType.PASTILHA -> when (inputs.pastilhaFormato) {
                PastilhaFormato.P5 -> 5.0
                PastilhaFormato.P7_5, PastilhaFormato.P10 -> 6.0
                null -> 5.0
            }

            RevestimentoType.PEDRA -> 20.0

            RevestimentoType.PISO_INTERTRAVADO -> 60.0

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> {
                val amb = inputs.ambiente
                val aplic = inputs.aplicacao

                when {
                    // Fallback neutro enquanto ainda não escolheu tudo
                    amb == null || aplic == null -> 20.0

                    // Piso
                    aplic == AplicacaoType.PISO && (amb == AmbienteType.SECO || amb == AmbienteType.SEMI) ->
                        18.0

                    aplic == AplicacaoType.PISO && amb == AmbienteType.MOLHADO ->
                        20.0

                    aplic == AplicacaoType.PISO && amb == AmbienteType.SEMPRE ->
                        22.0

                    // Parede
                    aplic == AplicacaoType.PAREDE && (amb == AmbienteType.SECO || amb == AmbienteType.SEMI) ->
                        15.0

                    aplic == AplicacaoType.PAREDE && amb == AmbienteType.MOLHADO ->
                        18.0

                    aplic == AplicacaoType.PAREDE && amb == AmbienteType.SEMPRE ->
                        22.0

                    else -> 20.0
                }
            }

            RevestimentoType.PISO -> {
                if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                    val maxLado =
                        kotlin.math.max(inputs.pecaCompCm ?: 0.0, inputs.pecaLargCm ?: 0.0)
                    when {
                        maxLado >= 90.0 -> 12.0
                        maxLado >= 60.0 -> 10.0
                        else -> 10.0
                    }
                } else 8.0
            }

            else -> 8.0
        }
    }

    /**
     * Retorna junta padrão em mm conforme tipo de revestimento
     */
    fun getJuntaPadraoMm(inputs: Inputs): Double {
        return when (inputs.revest) {
            RevestimentoType.PASTILHA -> 3.0
            RevestimentoType.PEDRA -> 4.0
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 2.5
            RevestimentoType.PISO_INTERTRAVADO -> 4.0
            RevestimentoType.PISO -> {
                if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO) 2.0 else 5.0
            }

            RevestimentoType.AZULEJO -> 5.0
            else -> 3.0
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