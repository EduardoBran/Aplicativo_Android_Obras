package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
import kotlin.math.max

/**
 * Calculadora específica para Pedra Portuguesa
 */
object PedraCalculator {

    private const val ESP_COLCHAO_PEDRA_M = 0.04

    data class TracoMix(
        val rotulo: String,
        val cimentoKgPorM3: Double,
        val areiaM3PorM3: Double
    )

    private val MIX_PEDRA_TRACO_13 = TracoMix("1:3", 430.0, 0.85)

    /** Processa materiais para Pedra Portuguesa */
    fun processarPedra(
        areaM2: Double,
        sobra: Double,
        inputs: Inputs,
        itens: MutableList<MaterialItem>
    ) {
        val mix = MIX_PEDRA_TRACO_13
        val d = (inputs.desnivelCm ?: 0.0)
        val leitoPedraCm = kotlin.math.round((max(4.0, d + 0.5) * 10.0)) / 10.0
        val leitoM = leitoPedraCm / 100.0
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)

        itens += MaterialItem(
            item = "Pedra (m²)",
            unid = "m²",
            qtd = NumberFormatter.arred2(areaCompraM2),
            observacao = "leito: ${NumberFormatter.arred1(leitoPedraCm)} cm • rejunte incluso no traço."
        )

        val (cimentoKg, areiaM3) = calcularCimentoEAreia(
            areaM2 = areaM2,
            sobra = sobra,
            inputs = inputs,
            mix = mix,
            leitoOverrideM = leitoM
        )
        adicionarCimentoEAreia(cimentoKg, areiaM3, itens)
        MaterialCalculator.adicionarEspacadoresECunhas(inputs, areaM2, sobra, itens)
    }

    /** Calcula cimento e areia necessários */
    fun calcularCimentoEAreia(
        areaM2: Double,
        sobra: Double,
        inputs: Inputs,
        mix: TracoMix,
        leitoOverrideM: Double? = null
    ): Pair<Double, Double> {
        val espColchaoM = leitoOverrideM ?: when (inputs.revest) {
            RevestimentoType.PEDRA -> ESP_COLCHAO_PEDRA_M
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 0.03
            else -> 0.0
        }

        val espPecaMm = inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs)
        val juntaMm = inputs.juntaMm ?: RevestimentoSpecifications.getJuntaPadraoMm(inputs)

        val volumeColchao = areaM2 * espColchaoM

        // Somar volume de juntas apenas para PEDRA
        val volumeJuntas = when (inputs.revest) {
            RevestimentoType.PEDRA -> volumeJuntasM3(areaM2, juntaMm, espPecaMm)
            RevestimentoType.MARMORE, RevestimentoType.GRANITO -> 0.0
            else -> 0.0
        }

        val volumeArgamassaTotal = (volumeColchao + volumeJuntas) * (1 + sobra / 100.0)

        val cimentoKg = volumeArgamassaTotal * mix.cimentoKgPorM3
        val areiaM3 = volumeArgamassaTotal * mix.areiaM3PorM3
        return cimentoKg to areiaM3
    }

    /** Calcula volume de juntas em m³ */
    private fun volumeJuntasM3(
        areaM2: Double,
        juntaMm: Double,
        espPecaMm: Double,
        passoMedioM: Double = 0.08
    ): Double {
        val w = (juntaMm.coerceAtLeast(0.5)) / 1000.0
        val a = passoMedioM.coerceIn(0.05, 0.20)
        val f = (2.0 * w / a - (w * w) / (a * a)).coerceIn(0.0, 0.35)
        val esp = (espPecaMm.coerceAtLeast(3.0)) / 1000.0
        return areaM2 * f * esp
    }

    /**  Adiciona cimento e areia à lista de materiais */
    fun adicionarCimentoEAreia(
        cimentoKg: Double,
        areiaM3: Double,
        itens: MutableList<MaterialItem>
    ) {
        itens += MaterialItem(
            item = "Cimento",
            unid = "kg",
            qtd = NumberFormatter.arred1(cimentoKg),
            observacao = "Utilizado para preparo do assentamento."
        )

        itens += MaterialItem(
            item = "Areia",
            unid = "m³",
            qtd = NumberFormatter.arred3(areiaM3),
            observacao = "Volume de areia para preparo do assentamento."
        )
    }
}