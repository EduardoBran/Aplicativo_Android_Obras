package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
import kotlin.math.max

/**
 * Calculadora específica para Mármore e Granito
 *
 * Regras:
 * - Ambientes Seco / Semi:
 *   • Peça < 90 (Seco) ou <= 90 (Semi), Esp ≤ 20 mm, Desnível < 1 cm → somente argamassa ACIII
 *   • Caso contrário, quando Esp ≥ 21 mm ou Desnível ≥ 1 cm → areia + cimento + argamassa ACIII
 *
 * - Ambiente Molhado:
 *   • Sempre areia + cimento + argamassa ACIII
 *
 * - Ambiente Sempre Molhado:
 *   • Sempre areia + cimento + argamassa ACIII
 *
 * Em todos cenários com Mármore/Granito onde há argamassa,
 * a observação da argamassa deve conter "Utilize ACIII." (feito em MaterialCalculator).
 */
object MarmoreGranitoCalculator {

    /**
     * Processa materiais para Mármore ou Granito
     */
    fun processarMarmoreOuGranito(
        inputs: Inputs,
        areaRevestM2: Double,
        areaMateriaisM2: Double,
        areaEspacadoresM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ): String? {
        if (areaRevestM2 <= 0.0) return null
        if (inputs.revest != RevestimentoType.MARMORE &&
            inputs.revest != RevestimentoType.GRANITO
        ) return null

        val nome = if (inputs.revest == RevestimentoType.MARMORE)
            "Mármore (m²)" else "Granito (m²)"

        // Item principal de revestimento (m²)
        val qtdPecas = MaterialCalculator.calcularQuantidadePecas(inputs, areaRevestM2, sobra)
        val areaCompraM2 = areaRevestM2 * (1 + sobra / 100.0)

        val obsRevest = MaterialCalculator.buildObservacaoRevestimento(
            sobra = sobra,
            qtdPecas = qtdPecas,
            pecasPorCaixa = inputs.pecasPorCaixa,
            pecaCompCm = inputs.pecaCompCm,
            pecaLargCm = inputs.pecaLargCm
        )

        val usoLeito = shouldUseLeitoAreiaCimento(inputs)
        val leitoCm = if (usoLeito) mgLeitoCm(inputs) else null

        val obsExtra = if (usoLeito) {
            // usoLeito => leitoCm sempre preenchido
            "leito: ${NumberFormatter.arred1(leitoCm!!)} cm"
        } else {
            // cenário de somente argamassa (dupla colagem)
            "Dupla colagem"
        }

        val observacaoFinal = buildString {
            if (obsRevest.isNotBlank()) append(obsRevest)
            if (obsExtra.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(obsExtra)
            }
        }.ifBlank { null }

        // Item principal de revestimento
        itens += MaterialItem(
            item = nome + RevestimentoSpecifications.tamanhoSufixo(inputs),
            unid = "m²",
            qtd = NumberFormatter.arred2(areaCompraM2),
            observacao = observacaoFinal
        )

        // ===== ARGAMASSA (sempre ACIII para Mármore/Granito) =====
        val inputsAc3 = inputs.copy(classeArgamassa = "ACIII")

        MaterialCalculator.adicionarArgamassaColante(
            inputs = inputsAc3,
            areaM2 = areaMateriaisM2,
            sobra = sobra,
            itens = itens
        )

        // ===== AREIA + CIMENTO (quando regras exigirem leito) =====
        if (usoLeito) {
            val (cimentoKg, areiaM3) = PedraCalculator.calcularCimentoEAreia(
                areaM2 = areaEspacadoresM2,
                sobra = sobra,
                inputs = inputs,
                mix = PedraCalculator.TracoMix("1:3", 430.0, 0.85),
                leitoOverrideM = (leitoCm ?: 3.0) / 100.0
            )
            PedraCalculator.adicionarCimentoEAreia(cimentoKg, areiaM3, itens)
        }

        // ===== REJUNTE =====
        MaterialCalculator.adicionarRejunte(inputs, areaMateriaisM2, itens)

        // ===== ESPAÇADORES / CUNHAS =====
        MaterialCalculator.adicionarEspacadoresECunhas(
            inputs,
            areaEspacadoresM2,
            sobra,
            itens
        )

        // Fixador mecânico (pino ou grampo) – apenas parede e peças > 30 kg.
        // Usa a área "principal" (sem rodapé), o que não altera cenários de piso.
        adicionarFixadorMecanicoSeNecessario(inputs, areaEspacadoresM2, sobra, itens)

        // Sempre retornamos ACIII para MG
        return "ACIII"
    }

    /**
     * Regras de quando deve usar leito de areia + cimento (sempre acompanhado de ACIII)
     * para Mármore/Granito.
     */
    private fun shouldUseLeitoAreiaCimento(inputs: Inputs): Boolean {
        val amb = inputs.ambiente ?: return false

        val maxLado = max(inputs.pecaCompCm ?: 0.0, inputs.pecaLargCm ?: 0.0)
        val espMm = inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs)
        val desnivel = inputs.desnivelCm ?: 0.0

        return when (amb) {
            AmbienteType.SECO -> {
                // Somente argamassa se:
                // Peça < 90, Espessura <= 20, Desnível < 1
                val onlyArg =
                    maxLado < 90.0 && espMm <= 20.0 && desnivel < 1.0
                // Se não for esse caso E (esp >= 21 OU desnivel >= 1) → leito
                !onlyArg && (espMm >= 21.0 || desnivel >= 1.0)
            }

            AmbienteType.SEMI -> {
                // Somente argamassa se:
                // Peça <= 90, Espessura <= 20, Desnível < 1
                val onlyArg =
                    maxLado <= 90.0 && espMm <= 20.0 && desnivel < 1.0
                !onlyArg && (espMm >= 21.0 || desnivel >= 1.0)
            }

            AmbienteType.MOLHADO -> true
            AmbienteType.SEMPRE -> true
        }
    }

    /**
     * Calcula espessura do leito para Mármore/Granito quando usa areia + cimento.
     */
    private fun mgLeitoCm(inputs: Inputs): Double {
        val d = inputs.desnivelCm ?: 0.0
        val leito = max(3.0, d + 0.5)
        return NumberFormatter.arred1(leito)
    }

    /**
     * Adiciona item "Fixador Mecânico (pino ou grampo)" quando aplicável
     * - Apenas Mármore / Granito em PAREDE
     * - Apenas se peso da peça > 30 kg
     */
    private fun adicionarFixadorMecanicoSeNecessario(
        inputs: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        // Apenas Parede
        if (inputs.aplicacao != AplicacaoType.PAREDE) return

        // Garantir que estamos em Mármore ou Granito
        if (inputs.revest != RevestimentoType.MARMORE &&
            inputs.revest != RevestimentoType.GRANITO
        ) return

        val compCm = inputs.pecaCompCm ?: return
        val largCm = inputs.pecaLargCm ?: return
        val espMm = inputs.pecaEspMm ?: return

        // Conversão para metros
        val compM = compCm / 100.0
        val largM = largCm / 100.0
        val espM = espMm / 1000.0

        // Peso em kg (densidade aproximada 2.700 kg/m³)
        val pesoKg = compM * largM * espM * 2700.0
        if (pesoKg <= 30.0) return

        // Quantidade de fixadores
        val fixadoresPorM2 = 4.0
        val areaCompraM2 = areaM2 * (1 + sobra / 100.0)
        val qtdFixadores = kotlin.math.ceil(areaCompraM2 * fixadoresPorM2).toInt()

        if (qtdFixadores <= 0) return

        itens += MaterialItem(
            item = "Fixador Mecânico (pino ou grampo)",
            unid = "un",
            qtd = qtdFixadores.toDouble(),
            observacao = "Recomendado para peças pesadas. Definir o tipo em obra."
        )
    }
}
