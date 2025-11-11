package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.*
import kotlin.math.ceil
import kotlin.math.max

/**
 * Calculadora principal de materiais para revestimentos padrão
 */
object MaterialCalculator {

    /**
     * Calcula quantidade de peças necessárias
     */
    fun calcularQuantidadePecas(
        inputs: Inputs,
        areaM2: Double,
        sobra: Double
    ): Double? {
        if (inputs.pecaCompCm == null || inputs.pecaLargCm == null) return null

        val areaPecaM2 = (inputs.pecaCompCm / 100.0) * (inputs.pecaLargCm / 100.0)
        val pecasNecessarias = ceil((areaM2 / areaPecaM2) * (1 + sobra / 100.0))

        return if (inputs.pecasPorCaixa != null && inputs.pecasPorCaixa > 0) {
            val caixas = ceil(pecasNecessarias / inputs.pecasPorCaixa).toInt()
            (caixas * inputs.pecasPorCaixa).toDouble()
        } else pecasNecessarias
    }

    /**
     * Adiciona argamassa colante à lista de materiais
     */
    fun adicionarArgamassaColante(
        inputs: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>,
        extraKg: Double = 0.0
    ) {
        val consumoArgKgM2 = ArgamassaSpecifications.consumoArgamassaKgM2(inputs)
        val totalArgKg = (consumoArgKgM2 * areaM2 * (1 + sobra / 100.0)) + extraKg

        val baseObs = "Consumo estimado para assentamento do revestimento."
        val obs = if (inputs.revest == RevestimentoType.MARMORE || inputs.revest == RevestimentoType.GRANITO) {
            "$baseObs\nUtilize ACIII."
        } else {
            baseObs
        }

        itens += MaterialItem(
            item = "Argamassa",
            unid = "kg",
            qtd = arred1(max(0.0, totalArgKg)),
            observacao = obs
        )
    }

    /**
     * Adiciona rejunte à lista de materiais
     */
    fun adicionarRejunte(
        inputs: Inputs,
        areaM2: Double,
        itens: MutableList<MaterialItem>
    ) {
        val spec = RejunteSpecifications.rejunteSpec(inputs)
        val consumoRejKgM2 = RejunteSpecifications.consumoRejunteKgM2(inputs, spec.densidade)
        val sobraUsuarioPct = inputs.sobraPct ?: 10.0
        val totalRejKg = consumoRejKgM2 * areaM2 * (1 + sobraUsuarioPct / 100.0)

        val observacaoRejunte = when {
            inputs.ambiente == AmbienteType.SECO &&
                    spec.nome.contains("Tipo 1", ignoreCase = true) ->
                "Considera junta, formato das peças e sobra.\nIndicado para áreas secas."

            (inputs.ambiente == AmbienteType.SEMI || inputs.ambiente == AmbienteType.MOLHADO) &&
                    spec.nome.contains("Tipo 2", ignoreCase = true) ->
                "Considera junta, formato das peças e sobra.\nIndicado para áreas úmidas."

            inputs.ambiente == AmbienteType.SEMPRE &&
                    spec.nome.contains("epóxi", ignoreCase = true) ->
                "Considera junta, formato das peças e sobra.\nIndicado para áreas sempre molhadas."

            else ->
                "Considera junta, formato das peças e sobra."
        }

        itens += MaterialItem(
            item = spec.nome,
            unid = "kg",
            qtd = arred1(max(0.0, totalRejKg)),
            observacao = observacaoRejunte
        )
    }

    /**
     * Adiciona espaçadores e cunhas à lista de materiais
     */
    fun adicionarEspacadoresECunhas(
        inputs: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (inputs.revest == RevestimentoType.PASTILHA) return
        if (inputs.revest == RevestimentoType.PEDRA ||
            inputs.pecaCompCm == null || inputs.pecaLargCm == null ||
            (inputs.juntaMm ?: 0.0) <= 0.0
        ) return

        val areaPecaM2 = (inputs.pecaCompCm / 100.0) * (inputs.pecaLargCm / 100.0)
        val pecasNec = ceil((areaM2 / areaPecaM2) * (1 + sobra / 100.0))
        val espacadores = ceil(pecasNec * 3.0).toInt()
        val pacEsp100 = pacotesDe100Un(espacadores)
        val obsPacEsp = if (pacEsp100 == 1)
            "1 pacote de 100 unidades."
        else
            "$pacEsp100 pacotes de 100 unidades."

        itens += MaterialItem(
            item = "Espaçadores",
            unid = "un",
            qtd = espacadores.toDouble(),
            observacao = obsPacEsp
        )

        if (inputs.revest == RevestimentoType.PISO || inputs.revest == RevestimentoType.AZULEJO) {
            itens += MaterialItem(
                item = "Cunhas",
                unid = "un",
                qtd = espacadores.toDouble(),
                observacao = obsPacEsp
            )
        }
    }

    /**
     * Adiciona impermeabilização à lista de materiais
     */
    fun adicionarImpermeabilizacao(
        inputs: Inputs,
        areaTotal: Double,
        itens: MutableList<MaterialItem>
    ) {
        if (!inputs.impermeabilizacaoOn) return

        val config = ImpermeabilizacaoSpecifications.getImpConfig(inputs.ambiente) ?: return
        val totalUsar = config.consumo * areaTotal

        itens += MaterialItem(
            item = config.item,
            unid = config.unid,
            qtd = arred1(totalUsar),
            observacao = config.observacao
        )
    }

    /**
     * Constrói observação do revestimento
     */
    fun buildObservacaoRevestimento(
        sobra: Double,
        qtdPecas: Double?,
        pecasPorCaixa: Int?,
        pecaCompCm: Double?,
        pecaLargCm: Double?
    ): String {
        val sb = StringBuilder()

        val pecasPorM2 = if (pecaCompCm != null && pecaLargCm != null &&
            pecaCompCm > 0 && pecaLargCm > 0
        ) {
            10000.0 / (pecaCompCm * pecaLargCm)
        } else null

        if (pecasPorM2 != null) {
            sb.append("Peças por m²: ${arred2(pecasPorM2)}")
        } else {
            sb.append("sobra: ${arred2(sobra)}%")
        }

        if (qtdPecas != null && qtdPecas > 0) {
            sb.append(" • ${qtdPecas.toInt()} peças.")
            if (pecasPorCaixa != null && pecasPorCaixa > 0) {
                val caixas = ceil(qtdPecas / pecasPorCaixa).toInt()
                sb.append(" (${caixas} caixas.)")
            }
        }

        return sb.toString()
    }

    private fun pacotesDe100Un(quantUn: Int) = ceilPos(quantUn / 100.0)
    private fun ceilPos(v: Double) = max(0, ceil(v).toInt())
    private fun arred1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
    private fun arred2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}