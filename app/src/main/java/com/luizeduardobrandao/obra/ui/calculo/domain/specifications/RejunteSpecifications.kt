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

    /** Retorna especificação de rejunte conforme ambiente */
    fun rejunteSpec(inputs: Inputs): RejunteSpec {
        return when (inputs.ambiente) {
            AmbienteType.SEMPRE ->
                RejunteSpec(
                    "Rejunte Epóxi",
                    CalcRevestimentoRules.Rejunte.DENS_EPOXI_KG_DM3,
                    CalcRevestimentoRules.Rejunte.EMB_EPOXI_KG
                )

            AmbienteType.SEMI,
            AmbienteType.MOLHADO ->
                RejunteSpec(
                    "Rejunte Comum Tipo 2",
                    CalcRevestimentoRules.Rejunte.DENS_CIMENTICIO_KG_DM3,
                    CalcRevestimentoRules.Rejunte.EMB_CIMENTICIO_KG
                )

            else ->
                RejunteSpec(
                    "Rejunte Comum Tipo 1",
                    CalcRevestimentoRules.Rejunte.DENS_CIMENTICIO_KG_DM3,
                    CalcRevestimentoRules.Rejunte.EMB_CIMENTICIO_KG
                )
        }
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