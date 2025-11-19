package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*

/**
 * Especificações e cálculos de rejunte
 */
object RejunteSpecifications {

    private const val DENS_EPOXI = 1700.0
    private const val DENS_CIMENTICIO = 1900.0
    private const val EMB_EPOXI_KG = 1.0
    private const val EMB_CIME_KG = 5.0

    data class RejunteSpec(
        val nome: String,
        val densidade: Double,
        val packKg: Double
    )

    /** Retorna especificação de rejunte conforme ambiente */
    fun rejunteSpec(inputs: Inputs): RejunteSpec {
        return when (inputs.ambiente) {
            AmbienteType.SEMPRE ->
                RejunteSpec("Rejunte Epóxi", DENS_EPOXI, EMB_EPOXI_KG)

            AmbienteType.SEMI, AmbienteType.MOLHADO ->
                RejunteSpec("Rejunte Comum Tipo 2", DENS_CIMENTICIO, EMB_CIME_KG)

            else ->
                RejunteSpec("Rejunte Comum Tipo 1", DENS_CIMENTICIO, EMB_CIME_KG)
        }
    }

    /** Calcula consumo de rejunte em kg/m² */
    fun consumoRejunteKgM2(inputs: Inputs, densidadeKgDm3: Double): Double {
        val juntaMm = inputs.juntaMm ?: RevestimentoSpecifications.getJuntaPadraoMm(inputs)
        val juntaM = (juntaMm.coerceAtLeast(0.5)) / 1000.0

        val (compM, largM, espM) = if (inputs.revest == RevestimentoType.PASTILHA) {
            val formato = inputs.pastilhaFormato
            val ladoCm = formato?.ladoCm ?: 5.0
            val comp = ladoCm / 100.0
            val larg = ladoCm / 100.0
            val esp = ((inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs))
                .coerceAtLeast(3.0)) / 1000.0
            Triple(comp, larg, esp)
        } else {
            val comp = (inputs.pecaCompCm ?: 30.0) / 100.0
            val larg = (inputs.pecaLargCm ?: 30.0) / 100.0
            val esp = ((inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs))
                .coerceAtLeast(3.0)) / 1000.0
            Triple(comp, larg, esp)
        }

        val consumo = ((compM + largM) / (compM * largM)) * juntaM * espM * densidadeKgDm3
        return consumo.coerceIn(0.10, 3.0)
    }
}