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
        if (!inputs.rodapeEnable || inputs.revest !in RevestimentoSpecifications.tiposComRodape()) return 0.0

        return if (inputs.rodapePerimetroAuto) {
            inputs.areaInformadaM2?.takeIf { it > 0 }?.let { 4 * sqrt(it) }
                ?: inputs.compM?.let { c -> inputs.largM?.let { l -> 2 * (c + l) } }
        } else inputs.rodapePerimetroManualM
    }

    /**
     * Calcula área base do rodapé para exibição em m²
     */
    fun rodapeAreaBaseExibicaoM2(inputs: Inputs): Double {
        if (!inputs.rodapeEnable || inputs.revest !in RevestimentoSpecifications.tiposComRodape()) return 0.0

        inputs.areaInformadaM2?.takeIf { it > 0 }?.let { return it }

        val (c, l) = inputs.compM to inputs.largM
        return if (c != null && l != null) c * l else 0.0
    }

    /**
     * Calcula perímetro seguro do rodapé para compra (com margem de segurança)
     */
    fun rodapePerimetroSeguroM(inputs: Inputs): Double? {
        if (!inputs.rodapeEnable || inputs.revest !in RevestimentoSpecifications.tiposComRodape()) return 0.0
        if (!inputs.rodapePerimetroAuto) return inputs.rodapePerimetroManualM

        val (c, l) = inputs.compM to inputs.largM

        val k = if (c != null && l != null) {
            val ratio = if (c > l) c / l else l / c
            if (ratio >= 2.0) 1.50 else 1.25
        } else 1.25

        return inputs.areaInformadaM2?.takeIf { it > 0 }?.let { k * 4 * sqrt(it) }
            ?: if (c != null && l != null) 2 * (c + l) else null
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
        if (!inputs.rodapeEnable || inputs.revest !in RevestimentoSpecifications.tiposComRodape()) return
        val alturaCm = inputs.rodapeAlturaCm ?: return
        if (areaCompraM2 <= 0.0 || perimetroLiquidoM <= 0.0) return

        val aberturaM = inputs.rodapeDescontarVaoM.takeIf { it > 0.0 }

        if (inputs.rodapeMaterial == RodapeMaterial.MESMA_PECA) {
            val areaComSobra = areaCompraM2 * (1 + sobra / 100.0)

            val obs = if (aberturaM != null) {
                "Mesma peça • Altura: ${arred0(alturaCm)}cm • Abertura: ${arred2(aberturaM)}m.\nIncluso na quantidade de peças."
            } else {
                "Mesma peça • Altura: ${arred0(alturaCm)}cm.\nIncluso na quantidade de peças."
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
                "Peça pronta • ${arred0(alturaCm)} x ${arred0(compCm)} cm • Abertura: ${arred2(aberturaM)}m.\n$qtdPecas peças."
            } else {
                "Peça pronta • ${arred0(alturaCm)} x ${arred0(compCm)} cm • $qtdPecas peças."
            }

            itens += MaterialItem(
                item = "Rodapé",
                unid = "m²",
                qtd = arred2(areaTotalM2),
                observacao = obs
            )
        }
    }

    /**
     * Adiciona informações do rodapé ao resumo
     */
    fun appendRodapeInfo(sb: StringBuilder, inputs: Inputs) {
        val perimetro = rodapePerimetroM(inputs) ?: return
        val alturaCm = inputs.rodapeAlturaCm ?: return
        val alturaM = alturaCm / 100.0
        val areaM2 = perimetro * alturaM

        if (inputs.rodapeMaterial == RodapeMaterial.PECA_PRONTA) {
            sb.appendLine("• 📏 Rodapé: ${arred2(areaM2)} m²\n(peça pronta)")
        } else {
            val areaBaseM2 = rodapeAreaBaseExibicaoM2(inputs)
            sb.appendLine(
                "• 📏 Rodapé: ${arred2(areaBaseM2)} m² × ${arred1(alturaCm)} cm = " +
                        "${arred2(areaM2)} m²\n(mesma peça)"
            )
        }
    }

    private fun arred0(v: Double) = kotlin.math.round(v)
    private fun arred1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}