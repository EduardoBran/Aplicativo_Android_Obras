package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import kotlin.math.sqrt

/** Utilitário para cálculos de Medidas da Área */
object AreaCalculator {

    /** Calcula área de paredes (com quantidade de paredes e aberturas) */
    private fun areaParedeM2(inputs: Inputs): Double? {
        val c = inputs.compM ?: return null
        val h = inputs.altM ?: return null
        val paredes = inputs.paredeQtd ?: return null
        if (paredes !in 1..20) return null

        val areaBruta = c * h * paredes
        if (areaBruta <= 0.0) return null

        val abertura = inputs.aberturaM2 ?: 0.0
        if (abertura < 0.0 || abertura > areaBruta) return null

        val areaLiquida = areaBruta - abertura
        return if (areaLiquida > 0.0) areaLiquida else null
    }

    /** Calcula área de ambiente plano (piso, etc.) com possível abertura */
    private fun areaRetangularComAbertura(
        compM: Double?,
        largM: Double?,
        aberturaM2: Double?
    ): Double? {
        val c = compM ?: return null
        val l = largM ?: return null

        val areaBruta = c * l
        if (areaBruta <= 0.0) return null

        val abertura = aberturaM2 ?: 0.0
        if (abertura < 0.0 || abertura > areaBruta) return null

        val areaLiquida = areaBruta - abertura
        return if (areaLiquida > 0.0) areaLiquida else null
    }

    /** Calcula área base do ambiente em m² */
    fun areaBaseM2(inputs: Inputs): Double? {
        // 1) Área total informada tem prioridade (modo "Prefere informar área em m²?")
        inputs.areaInformadaM2
            ?.takeIf { it > 0.0 }
            ?.let { areaInformada ->
                val abertura = inputs.aberturaM2 ?: 0.0
                if (abertura < 0.0 || abertura > areaInformada) return null

                val areaLiquida = areaInformada - abertura
                return if (areaLiquida > 0.0) areaLiquida else null
            }

        val c = inputs.compM
        val l = inputs.largM

        return when (inputs.revest) {
            // Azulejo e Pastilha: sempre parede
            RevestimentoType.AZULEJO,
            RevestimentoType.PASTILHA -> areaParedeM2(inputs)

            // Mármore / Granito dependem da aplicação
            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO -> when (inputs.aplicacao) {
                AplicacaoType.PAREDE -> areaParedeM2(inputs)
                AplicacaoType.PISO -> areaRetangularComAbertura(c, l, inputs.aberturaM2)
                else -> null
            }
            // Demais: lógica plana (piso, pedra, intertravado, etc.) com possível abertura
            else -> areaRetangularComAbertura(c, l, inputs.aberturaM2)
        }
    }

    /** Retorna o perímetro máximo possível de rodapé (em metros) */
    fun getRodapePerimetroPossivel(inputs: Inputs): Double? {
        if (!inputs.rodapeEnable) return null

        // Perímetro manual informado
        if (!inputs.rodapePerimetroAuto && inputs.rodapePerimetroManualM != null
            && inputs.rodapePerimetroManualM > 0.0
        ) {
            return inputs.rodapePerimetroManualM
        }
        val comp = inputs.compM
        val larg = inputs.largM

        // Cálculo automático: 2 × (comp + larg)
        if (comp != null && larg != null && comp > 0.0 && larg > 0.0) {
            return 2.0 * (comp + larg)
        }

        // Área informada: assume ambiente quadrado
        val area = inputs.areaInformadaM2
        if (area != null && area > 0.0) {
            return 4.0 * sqrt(area)
        }

        return null
    }
}