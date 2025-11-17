package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules
import java.util.Locale

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
        val ladoCm: Double,          // Lado 1 da peça (cm)
        val lado2Cm: Double,         // Lado 2 da peça (cm) – igual ao ladoCm em formatos quadrados
        val mantaCompCm: Double,     // Comprimento da manta (cm)
        val mantaLargCm: Double,     // Largura da manta (cm)
        val espMmPadrao: Double      // Espessura padrão (mm) para este formato
    ) {
        // 🔹 NOVOS formatos (não confundem com os atuais de cerâmica)
        P1_5(
            ladoCm = 1.5,
            lado2Cm = 1.5,
            mantaCompCm = 32.1,
            mantaLargCm = 32.1,
            espMmPadrao = PecaRules.PASTILHA_ESP_P1_5_MM
        ),
        P2(
            ladoCm = 2.0,
            lado2Cm = 2.0,
            mantaCompCm = 34.2,
            mantaLargCm = 34.2,
            espMmPadrao = PecaRules.PASTILHA_ESP_P2_MM
        ),
        P2_2(
            ladoCm = 2.5,
            lado2Cm = 2.5,
            mantaCompCm = 33.3,
            mantaLargCm = 33.3,
            espMmPadrao = PecaRules.PASTILHA_ESP_P2_2_MM
        ),
        P2_5(
            ladoCm = 2.5,
            lado2Cm = 5.0,
            mantaCompCm = 33.3,
            mantaLargCm = 31.5,
            espMmPadrao = PecaRules.PASTILHA_ESP_P2_5_MM
        ),
        P5_5(
            ladoCm = 5.0,
            lado2Cm = 5.0,
            mantaCompCm = 31.5,
            mantaLargCm = 31.5,
            espMmPadrao = PecaRules.PASTILHA_ESP_P5_5_MM
        ),
        P5_10(
            ladoCm = 5.0,
            lado2Cm = 10.0,
            mantaCompCm = 31.5,
            mantaLargCm = 30.6,
            espMmPadrao = PecaRules.PASTILHA_ESP_P5_5_10MM
        ),
        P5_15(
            ladoCm = 5.0,
            lado2Cm = 15.0,
            mantaCompCm = 31.5,
            mantaLargCm = 30.3,
            espMmPadrao = PecaRules.PASTILHA_ESP_P5_5_15MM
        ),
        P7_5P(
            ladoCm = 7.5,
            lado2Cm = 7.5,
            mantaCompCm = 30.9,
            mantaLargCm = 30.9,
            espMmPadrao = PecaRules.PASTILHA_ESP_P7_5PMM
        ),
        P10P(
            ladoCm = 10.0,
            lado2Cm = 10.0,
            mantaCompCm = 30.6,
            mantaLargCm = 30.6,
            espMmPadrao = PecaRules.PASTILHA_ESP_P10PMM
        ),

        // 🔹 FORMATOS ATUAIS (cerâmica) – mantidos como estão
        P5(
            ladoCm = 5.0,
            lado2Cm = 5.0,
            mantaCompCm = 32.5,
            mantaLargCm = 32.5,
            espMmPadrao = PecaRules.PASTILHA_ESP_P5_MM
        ),
        P7_5(
            ladoCm = 7.5,
            lado2Cm = 7.5,
            mantaCompCm = 31.5,
            mantaLargCm = 31.5,
            espMmPadrao = PecaRules.PASTILHA_ESP_P7_5_MM
        ),
        P10(
            ladoCm = 10.0,
            lado2Cm = 10.0,
            mantaCompCm = 31.0,
            mantaLargCm = 31.0,
            espMmPadrao = PecaRules.PASTILHA_ESP_P10_MM
        )
    }

    /**
     * Monta o nome completo da pastilha para exibir na tabela de materiais.
     *
     * Ex: "Pastilha 5cm × 5cm (32,5cm × 32,5cm)"
     *     "Pastilha 10cm × 10cm (31cm × 31cm)"
     */
    fun getPastilhaNomeCompleto(formato: PastilhaFormato): String {
        val lado1Str = formatMedidaCm(formato.ladoCm)
        val lado2Str = formatMedidaCm(formato.lado2Cm)
        val mantaCompStr = formatMedidaCm(formato.mantaCompCm)
        val mantaLargStr = formatMedidaCm(formato.mantaLargCm)

        return "Pastilha ${lado1Str}cm × ${lado2Str}cm (${mantaCompStr}cm × ${mantaLargStr}cm)"
    }

    /**
     * Retorna espessura padrão em mm conforme tipo de revestimento
     */
    fun getEspessuraPadraoMm(inputs: Inputs): Double {
        return when (inputs.revest) {
            RevestimentoType.PASTILHA -> {
                // Agora a espessura padrão vem diretamente do formato escolhido
                inputs.pastilhaFormato?.espMmPadrao ?: PecaRules.PASTILHA_ESP_P5_MM
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
            RevestimentoType.PISO -> {
                if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO)
                    JuntaPadrao.PISO_PORCELANATO_MM
                else
                    JuntaPadrao.PISO_CERAMICO_MM
            }

            RevestimentoType.AZULEJO -> {
                when (inputs.pisoPlacaTipo) {
                    PlacaTipo.PORCELANATO -> JuntaPadrao.AZULEJO_PORCELANATO_MM
                    PlacaTipo.CERAMICA, null -> JuntaPadrao.AZULEJO_CERAMICO_MM
                }
            }

            RevestimentoType.PASTILHA -> {
                when (inputs.pisoPlacaTipo) {
                    PlacaTipo.PORCELANATO -> JuntaPadrao.PASTILHA_PORCELANATO_MM
                    PlacaTipo.CERAMICA, null -> JuntaPadrao.PASTILHA_CERAMICO_MM
                }
            }

            RevestimentoType.PEDRA -> JuntaPadrao.PEDRA_MM
            RevestimentoType.PISO_INTERTRAVADO -> JuntaPadrao.INTERTRAVADO_MM
            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> JuntaPadrao.MG_MM

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

    /**
     * Formata medidas em cm para exibição:
     * - sem casas decimais se for inteiro (31.0 -> "31")
     * - 1 casa decimal se tiver fração (32.5 -> "32,5")
     */
    private fun formatMedidaCm(v: Double): String {
        val abs = kotlin.math.abs(v)
        val inteiro = abs % 1.0 == 0.0
        return if (inteiro) {
            abs.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", abs)
        }.replace('.', ',')
    }
}