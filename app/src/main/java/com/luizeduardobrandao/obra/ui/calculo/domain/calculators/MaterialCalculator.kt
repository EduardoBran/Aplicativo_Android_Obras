package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.*
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
import kotlin.math.ceil
import kotlin.math.max

/**
 * Calculadora principal de materiais para revestimentos padrão
 */
object MaterialCalculator {

    /** Calcula quantidade de peças necessárias */
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

    /** Adiciona argamassa colante à lista de materiais */
    fun adicionarArgamassaColante(
        inputs: Inputs,
        areaM2: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>,
        extraKg: Double = 0.0
    ) {
        val consumoArgKgM2 = ArgamassaSpecifications.consumoArgamassaKgM2(inputs)
        val totalArgKg = (consumoArgKgM2 * areaM2 * (1 + sobra / 100.0)) + extraKg

        val classeIndicada = ArgamassaSpecifications.classificarArgamassa(inputs)

        val obs = if (!classeIndicada.isNullOrBlank()) {
            "Argamassa tipo $classeIndicada sugerida.\nValidar na obra."
        } else {
            "Verificar tipo de argamassa na obra."
        }

        itens += MaterialItem(
            item = "Argamassa",
            unid = "kg",
            qtd = NumberFormatter.arred1(max(0.0, totalArgKg)),
            observacao = obs
        )
    }

    /** Adiciona rejunte à lista de materiais */
    fun adicionarRejunte(
        inputs: Inputs,
        areaM2: Double,
        itens: MutableList<MaterialItem>
    ) {
        val spec = RejunteSpecifications.rejunteSpec(inputs)
        val consumoRejKgM2 = RejunteSpecifications.consumoRejunteKgM2(inputs, spec.densidade)
        val sobraUsuarioPct = inputs.sobraPct ?: 10.0
        val totalRejKg = consumoRejKgM2 * areaM2 * (1 + sobraUsuarioPct / 100.0)

        val classe = spec.nome // "Tipo 1", "Tipo 2", "Epóxi" ou combinação

        val observacaoRejunte =
            if (classe.isNotBlank()) {
                "Rejunte tipo $classe sugerido.\nValidar na obra."
            } else {
                "Verificar tipo de rejunte na obra."
            }
        itens += MaterialItem(
            item = "Rejunte",
            unid = "kg",
            qtd = NumberFormatter.arred1(max(0.0, totalRejKg)),
            observacao = observacaoRejunte
        )
    }

    /** Adiciona espaçadores e cunhas à lista de materiais */
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
        val juntaMm = inputs.juntaMm ?: 0.0

        val obsEspacadores = "Espaçador de ${NumberFormatter.arred1(juntaMm)}mm.\nValidar na obra."
        itens += MaterialItem(
            item = "Espaçadores",
            unid = "un",
            qtd = espacadores.toDouble(),
            observacao = obsEspacadores
        )

        if (inputs.revest == RevestimentoType.PISO || inputs.revest == RevestimentoType.AZULEJO) {
            val obsCunhas = "Cunha para sistema nivelador."
            itens += MaterialItem(
                item = "Cunhas",
                unid = "un",
                qtd = espacadores.toDouble(),
                observacao = obsCunhas
            )
        }
    }

    /** Constrói observação do revestimento */
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
            sb.append("Peças por m²: ${NumberFormatter.arred2(pecasPorM2)}")
        } else {
            sb.append("sobra: ${NumberFormatter.arred2(sobra)}%")
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
}