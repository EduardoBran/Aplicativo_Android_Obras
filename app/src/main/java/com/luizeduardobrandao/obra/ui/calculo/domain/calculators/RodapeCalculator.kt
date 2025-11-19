package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Calculadora específica para Rodapé
 */
object RodapeCalculator {

    /**
     * Calcula perímetro do rodapé para exibição
     */
    fun rodapePerimetroM(inputs: Inputs): Double? {
        if (!inputs.rodapeEnable || !RevestimentoSpecifications.hasRodapeStep(inputs)) return 0.0

        return if (inputs.rodapePerimetroAuto) {
            inputs.areaInformadaM2?.takeIf { it > 0 }?.let { 4 * sqrt(it) }
                ?: inputs.compM?.let { c -> inputs.largM?.let { l -> 2 * (c + l) } }
        } else inputs.rodapePerimetroManualM
    }

    /**
     * Calcula área base do rodapé para exibição em m²
     */
    fun rodapeAreaBaseExibicaoM2(inputs: Inputs): Double {
        if (!inputs.rodapeEnable || !RevestimentoSpecifications.hasRodapeStep(inputs)) return 0.0

        inputs.areaInformadaM2?.takeIf { it > 0 }?.let { return it }

        val (c, l) = inputs.compM to inputs.largM
        return if (c != null && l != null) c * l else 0.0
    }

    /**
     * Adiciona rodapé à lista de materiais
     */
    fun adicionarRodape(
        inputs: Inputs,
        areaCompraM2: Double,
        perimetroLiquidoM: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (!inputs.rodapeEnable || !RevestimentoSpecifications.hasRodapeStep(inputs)) return
        val alturaCm = inputs.rodapeAlturaCm ?: return
        if (areaCompraM2 <= 0.0 || perimetroLiquidoM <= 0.0) return

        val aberturaM = inputs.rodapeDescontarVaoM.takeIf { it > 0.0 }

        if (inputs.rodapeMaterial == RodapeMaterial.MESMA_PECA) {
            val areaComSobra = areaCompraM2 * (1 + sobra / 100.0)

            val obs = if (aberturaM != null) {
                "Mesma peça • Altura: ${arred0(alturaCm)}cm • Abertura: ${arred2(aberturaM)}m.\nMateriais extras para colocação já incluídos."
            } else {
                "Mesma peça • Altura: ${arred0(alturaCm)}cm.\nMateriais extras para colocação já incluídos."
            }

            itens += MaterialItem(
                item = "Rodapé",
                unid = "m²",
                qtd = arred2(areaComSobra),
                observacao = obs
            )
        } else {
            val compM = inputs.rodapeCompComercialM ?: return
            val alturaM = alturaCm / 100.0

            val perimetroComSobra = perimetroLiquidoM * (1 + sobra / 100.0)
            val qtdPecas = ceil(perimetroComSobra / compM).toInt().coerceAtLeast(1)
            val areaTotalM2 = qtdPecas * compM * alturaM

            val compCm = compM * 100.0

            val obs = if (aberturaM != null) {
                "Peça pronta • ${arred0(alturaCm)} x ${arred0(compCm)} cm • Abertura: ${
                    arred2(
                        aberturaM
                    )
                }m.\n$qtdPecas peças.\n" +
                        "Materiais extras para colocação já incluídos."
            } else {
                "Peça pronta • ${arred0(alturaCm)} x ${arred0(compCm)} cm • $qtdPecas peças.\n" +
                        "Materiais extras para colocação já incluídos."
            }

            itens += MaterialItem(
                item = "Rodapé",
                unid = "m²",
                qtd = arred2(areaTotalM2),
                observacao = obs
            )
        }
    }

    private fun arred0(v: Double) = kotlin.math.round(v)
    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}