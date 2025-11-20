package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules

/**
 * Especificações e cálculos de rejunte
 */
object RejunteSpecifications {

    data class RejunteSpec(
        val nome: String,
        val densidade: Double,
        val packKg: Double
    )

    /** Retorna especificação de rejunte conforme ambiente / tipo / tamanho de peça */
    fun rejunteSpec(inputs: Inputs): RejunteSpec {
        val classe = classificarRejunte(inputs)

        // Fallback para garantir uma classe mesmo se faltar algum dado
        val classeFinal = classe ?: when (inputs.ambiente) {
            AmbienteType.SEMPRE -> "Epóxi"
            AmbienteType.SEMI,
            AmbienteType.MOLHADO -> "Tipo 2"

            else -> "Tipo 1"
        }

        // Para consumo / embalagem:
        // - "Epóxi"  → usa densidade/embalagem de epóxi
        // - "Tipo 1" ou "Tipo 2" ou combinações → densidade cimentícia
        val isEpoxi = classeFinal.equals("Epóxi", ignoreCase = true)

        val densidade = if (isEpoxi)
            CalcRevestimentoRules.Rejunte.DENS_EPOXI_KG_DM3
        else
            CalcRevestimentoRules.Rejunte.DENS_CIMENTICIO_KG_DM3

        val pack = if (isEpoxi)
            CalcRevestimentoRules.Rejunte.EMB_EPOXI_KG
        else
            CalcRevestimentoRules.Rejunte.EMB_CIMENTICIO_KG

        return RejunteSpec(
            nome = classeFinal,
            densidade = densidade,
            packKg = pack
        )
    }

    /** ======================= CLASSIFICAÇÃO DE REJUNTE =======================
     * Classes base: "Tipo 1", "Tipo 2", "Epóxi"
     */
    private fun classificarRejunte(inputs: Inputs): String? {
        val revest = inputs.revest ?: return null
        val ambiente = inputs.ambiente ?: return null

        // Tipo de material para Piso / Azulejo / Pastilha
        val tipoPlaca = when (revest) {
            RevestimentoType.PISO,
            RevestimentoType.AZULEJO,
            RevestimentoType.PASTILHA ->
                inputs.pisoPlacaTipo ?: PlacaTipo.CERAMICA // default: cerâmica
            else -> null
        }
        val isCer = tipoPlaca == PlacaTipo.CERAMICA
        val isPorc = tipoPlaca == PlacaTipo.PORCELANATO
        val ladoMax = ladoMaximoCm(inputs)

        return when (ambiente) {
            // ================== AMBIENTE MOLHADO / SEMPRE MOLHADO ==================
            AmbienteType.MOLHADO,
            AmbienteType.SEMPRE -> {
                // Regras especificadas: todos os casos → Epóxi
                "Epóxi"
            }

            // ================== AMBIENTE SECO ==================
            AmbienteType.SECO -> when (revest) {
                // ---------- PISO ----------
                RevestimentoType.PISO -> {
                    if (isCer) { // Cerâmica
                        val lado = ladoMax ?: return "Tipo 1"
                        when {
                            // Tipo 1 – Peça com lado máximo < 45 cm
                            lado < 45.0 -> "Tipo 1"
                            // Tipo 2 – Peça com lado máximo >= 45 cm e < 60
                            lado < 60.0 -> "Tipo 2"
                            // Tipo 2 ou Epóxi – Peça com lado máximo >= 60
                            else -> "Tipo 2 ou Epóxi"
                        }
                    } else if (isPorc) { // Porcelanato
                        val lado = ladoMax ?: return "Tipo 2"
                        // Tipo 2 – Peça com lado máximo < 60 cm
                        // Tipo 2 ou Epóxi – Peça com lado máximo >= 60
                        if (lado < 60.0) "Tipo 2" else "Tipo 2 ou Epóxi"
                    } else {
                        null
                    }
                }

                // ---------- AZULEJO ----------
                RevestimentoType.AZULEJO -> {
                    when {
                        isCer -> "Tipo 1"  // Cerâmica
                        isPorc -> "Tipo 2" // Porcelanato

                        else -> null
                    }
                }

                // ---------- PASTILHA ----------
                RevestimentoType.PASTILHA -> {
                    when {
                        isPorc -> "Tipo 2" // Porcelanato
                        else -> "Tipo 1"   // Cerâmica
                    }
                }

                // ---------- MÁRMORE E GRANITO ----------
                RevestimentoType.MARMORE,
                RevestimentoType.GRANITO -> "Tipo 2"

                else -> null
            }

            // ================== AMBIENTE SEMI MOLHADO ==================
            AmbienteType.SEMI -> when (revest) {
                // ---------- PISO ----------
                RevestimentoType.PISO -> {
                    if (isCer) { // Cerâmica
                        val lado = ladoMax ?: return "Tipo 2"
                        // Tipo 2 – lado < 45 cm
                        // Tipo 2 ou Epóxi – lado >= 45
                        if (lado < 45.0) "Tipo 2" else "Tipo 2 ou Epóxi"
                    } else if (isPorc) { // Porcelanato
                        val lado = ladoMax ?: return "Tipo 2"
                        // Tipo 2 – lado <= 60 cm
                        // Tipo 2 ou Epóxi – lado >= 60 cm
                        if (lado < 60.0) "Tipo 2" else "Tipo 2 ou Epóxi"
                    } else {
                        null
                    }
                }

                // ---------- AZULEJO ----------
                RevestimentoType.AZULEJO -> {
                    if (isCer) { // Cerâmica
                        "Tipo 2"
                    } else if (isPorc) { // Porcelanato
                        // Tipo 2 – lado < 60
                        // Tipo 2 ou Epóxi – lado >= 60
                        val lado = ladoMax ?: return "Tipo 2"
                        if (lado < 60.0) "Tipo 2" else "Tipo 2 ou Epóxi"
                    } else {
                        null
                    }
                }

                // ---------- PASTILHA ----------
                RevestimentoType.PASTILHA -> {
                    when {
                        // Porcelanato – Tipo 2 ou Epóxi
                        isPorc -> "Tipo 2 ou Epóxi"
                        // Pastilha Cerâmica – Tipo 2
                        else -> "Tipo 2"
                    }
                }

                // ---------- MÁRMORE E GRANITO ----------
                RevestimentoType.MARMORE,
                RevestimentoType.GRANITO -> "Tipo 2"

                else -> null
            }
        }
    }

    /** Maior lado da peça em cm, ou null se não informado / inválido */
    private fun ladoMaximoCm(inputs: Inputs): Double? {
        val comp = inputs.pecaCompCm
        val larg = inputs.pecaLargCm
        if (comp == null || larg == null) return null
        if (comp <= 0.0 || larg <= 0.0) return null
        return maxOf(comp, larg)
    }

    /** Calcula consumo de rejunte em kg/m² */
    fun consumoRejunteKgM2(inputs: Inputs, densidadeKgDm3: Double): Double {
        val juntaMm = inputs.juntaMm ?: RevestimentoSpecifications.getJuntaPadraoMm(inputs)
        val juntaM = (
                juntaMm.coerceAtLeast(CalcRevestimentoRules.Rejunte.JUNTA_MIN_MM)
                ) / 1000.0

        val (compM, largM, espM) = if (inputs.revest == RevestimentoType.PASTILHA) {
            val formato = inputs.pastilhaFormato
            val ladoCm = formato?.ladoCm ?: CalcRevestimentoRules.Rejunte.PASTILHA_LADO_DEFAULT_CM

            val comp = ladoCm / 100.0
            val larg = ladoCm / 100.0
            val esp = (
                    (inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs))
                        .coerceAtLeast(CalcRevestimentoRules.Rejunte.ESPESSURA_MIN_MM)
                    ) / 1000.0

            Triple(comp, larg, esp)
        } else {
            val comp = (
                    inputs.pecaCompCm ?: CalcRevestimentoRules.Rejunte.PECA_LADO_DEFAULT_CM
                    ) / 100.0
            val larg = (
                    inputs.pecaLargCm ?: CalcRevestimentoRules.Rejunte.PECA_LADO_DEFAULT_CM
                    ) / 100.0
            val esp = (
                    (inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs))
                        .coerceAtLeast(CalcRevestimentoRules.Rejunte.ESPESSURA_MIN_MM)
                    ) / 1000.0

            Triple(comp, larg, esp)
        }

        val consumo = ((compM + largM) / (compM * largM)) * juntaM * espM * densidadeKgDm3

        return consumo.coerceIn(
            CalcRevestimentoRules.Rejunte.CONSUMO_MIN_KG_M2,
            CalcRevestimentoRules.Rejunte.CONSUMO_MAX_KG_M2
        )
    }
}