package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.floor

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

        // Dimensões da peça (podem ser quadradas ou retangulares)
        val ladoPecaCompCm = formato.ladoCm
        val ladoPecaLargCm = formato.lado2Cm

        // Dimensões da manta
        val mantaCompCm = formato.mantaCompCm
        val mantaLargCm = formato.mantaLargCm

        val areaPecaM2 = (ladoPecaCompCm / 100.0) * (ladoPecaLargCm / 100.0)
        val areaMantaM2 = (mantaCompCm / 100.0) * (mantaLargCm / 100.0)

        // Peças por manta (aproximação por área, sempre >= 1)
        val pecasPorManta = max(1, floor(areaMantaM2 / areaPecaM2).toInt())

        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        // ✅ Calcula mantas por m² (não peças por m²)
        val mantasPorM2 = 1.0 / areaMantaM2
        val totalMantas = ceil(areaCompraM2 * mantasPorM2).toInt()
        val totalPecas = totalMantas * pecasPorManta

        // Nome baseado nas especificações (peça + manta)
        val nome = RevestimentoSpecifications.getPastilhaNomeCompleto(formato)

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

        // Argamassa: usa dimensões e espessura/junta adequadas ao formato
        val iArg = inputs.copy(
            juntaMm = (inputs.juntaMm
                ?: RevestimentoSpecifications.getJuntaPadraoMm(inputs)).coerceIn(1.0, 5.0),
            pecaEspMm = RevestimentoSpecifications.getEspessuraPadraoMm(inputs)
        )
        MaterialCalculator.adicionarArgamassaColante(iArg, areaM2, sobra, itens)

        // Rejunte: baseado na geometria da peça
        MaterialCalculator.adicionarRejunte(iArg, areaM2, itens)
    }

    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}