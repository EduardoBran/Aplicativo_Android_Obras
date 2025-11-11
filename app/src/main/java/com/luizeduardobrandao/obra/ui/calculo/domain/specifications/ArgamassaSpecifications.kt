package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import kotlin.math.max

/**
 * Especificações e cálculos de argamassa colante
 */
object ArgamassaSpecifications {

    private const val CONSUMO_ARGAMASSA_RODAPE_KG_M2 = 5.0

    /**
     * Calcula consumo de argamassa em kg/m²
     */
    fun consumoArgamassaKgM2(inputs: Inputs): Double {
        val maxLado = max(inputs.pecaCompCm ?: 30.0, inputs.pecaLargCm ?: 30.0)
        val isPorc = inputs.revest == RevestimentoType.PISO &&
                inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO
        val esp = inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs)

        // 🧱 Tratamento especial para pastilhas
        if (inputs.revest == RevestimentoType.PASTILHA) {
            return when (inputs.pecaCompCm) {
                5.0 -> 7.0
                7.5 -> 7.0
                10.0 -> 7.0
                else -> 5.5
            }
        }

        val consumoBase = when {
            maxLado <= 15.0 -> 4.0
            maxLado <= 20.0 -> 5.0
            maxLado <= 32.0 -> 6.0
            maxLado <= 45.0 -> 7.0
            maxLado <= 60.0 -> 8.0
            maxLado <= 75.0 -> 9.0
            maxLado <= 90.0 -> 10.0
            maxLado <= 120.0 -> 12.0
            else -> 14.0
        }

        val fatorPorcelanato = if (isPorc) when {
            maxLado >= 60.0 -> 1.20
            maxLado >= 45.0 -> 1.15
            else -> 1.10
        } else 1.0

        val fatorEspessura = when {
            esp < 7.0 -> 0.95
            esp <= 10.0 -> 1.0
            esp <= 15.0 -> 1.1
            else -> 1.2
        }

        val fatorAmbiente = when (inputs.ambiente) {
            AmbienteType.SEMPRE -> 1.15
            AmbienteType.MOLHADO -> 1.10
            else -> 1.0
        }

        return (consumoBase * fatorPorcelanato * fatorEspessura * fatorAmbiente)
            .coerceIn(4.0, 18.0)
    }

    /**
     * Gera MaterialItem de argamassa para rodapé
     */
    fun materialArgamassaRodape(rodapeAreaM2: Double): MaterialItem? {
        if (rodapeAreaM2 <= 0.0) return null

        val kgReal = rodapeAreaM2 * CONSUMO_ARGAMASSA_RODAPE_KG_M2

        return MaterialItem(
            item = "Argamassa colante (rodapé)",
            unid = "kg",
            qtd = arred1(kgReal),
            observacao = "Para assentamento do rodapé."
        )
    }

    private fun arred1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
}