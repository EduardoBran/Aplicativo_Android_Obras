package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import kotlin.math.ceil
import kotlin.math.max

/**
 * Calculadora específica para Pastilhas
 */
object PastilhaCalculator {

    /**
     * Processa materiais para Pastilha
     */
    fun processarPastilha(
        inputs: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        val formato = inputs.pastilhaFormato ?: return
        if (areaM2 <= 0.0) return

        val ladoPecaCm = formato.ladoCm
        val ladoMantaCm = formato.mantaLadoCm

        val areaPecaM2 = (ladoPecaCm / 100.0) * (ladoPecaCm / 100.0)
        val areaMantaM2 = (ladoMantaCm / 100.0) * (ladoMantaCm / 100.0)

        // Peças por manta (aproximação por área, sempre >= 1)
        val pecasPorManta = max(1, kotlin.math.floor(areaMantaM2 / areaPecaM2).toInt())

        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        // ✅ Calcula mantas por m² (não peças por m²)
        val mantasPorM2 = 1.0 / areaMantaM2
        val totalMantas = ceil(areaCompraM2 * mantasPorM2).toInt()
        val totalPecas = totalMantas * pecasPorManta

        val nome = when (formato) {
            RevestimentoSpecifications.PastilhaFormato.P5 -> "Pastilha 5cm × 5cm (32,5cm × 32,5cm)"
            RevestimentoSpecifications.PastilhaFormato.P7_5 -> "Pastilha 7,5cm × 7,5cm (31,5cm × 31,5cm)"
            RevestimentoSpecifications.PastilhaFormato.P10 -> "Pastilha 10cm × 10cm (31cm × 31cm)"
        }

        // ✅ Observação: Ordem comercial (mantas → peças)
        val observacao = buildString {
            append("Mantas por m²: ${arred2(mantasPorM2)}")
            append(" • $totalPecas peças.")
        }

        itens += MaterialItem(
            item = nome,
            unid = "m²",
            qtd = arred2(areaCompraM2),
            observacao = observacao
        )

        // Argamassa: usa dimensões da peça
        val iArg = inputs.copy(
            juntaMm = (inputs.juntaMm ?: RevestimentoSpecifications.getJuntaPadraoMm(inputs)).coerceIn(1.0, 5.0),
            pecaEspMm = RevestimentoSpecifications.getEspessuraPadraoMm(inputs)
        )
        MaterialCalculator.adicionarArgamassaColante(iArg, areaM2, sobra, itens)

        // Rejunte: baseado na geometria da peça
        MaterialCalculator.adicionarRejunte(iArg, areaM2, itens)
    }

    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}