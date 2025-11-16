package com.luizeduardobrandao.obra.ui.calculo.ui

import android.content.Context
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.AreaCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.RodapeCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import kotlin.math.round

/**
 * Monta o texto da Revisão de Parâmetros (Tela 7) usando strings.xml
 * e mantendo todos os ícones/emojis em um único lugar.
 *
 * Nenhuma regra de cálculo é alterada – apenas formatação de UI.
 */
object ReviewSummaryFormatter {

    fun buildResumoRevisao(context: Context, inputs: Inputs): CharSequence = buildString {
        val res = context.resources

        // ----------------- Revestimento -----------------
        val revestText = when (inputs.revest) {
            RevestimentoType.PISO -> {
                if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                    res.getString(R.string.calc_rev_revest_piso_porcelanato)
                } else {
                    res.getString(R.string.calc_rev_revest_piso_ceramico)
                }
            }

            RevestimentoType.AZULEJO ->
                res.getString(R.string.calc_rev_revest_azulejo)

            RevestimentoType.PASTILHA ->
                res.getString(R.string.calc_rev_revest_pastilha)

            RevestimentoType.PEDRA ->
                res.getString(R.string.calc_rev_revest_pedra)

            RevestimentoType.PISO_INTERTRAVADO ->
                res.getString(R.string.calc_rev_revest_piso_intertravado)

            RevestimentoType.MARMORE ->
                res.getString(R.string.calc_rev_revest_marmore)

            RevestimentoType.GRANITO ->
                res.getString(R.string.calc_rev_revest_granito)

            null ->
                res.getString(R.string.calc_rev_value_none)
        }
        appendLine(res.getString(R.string.calc_rev_line_revestimento, revestText))

        // ----------------- Ambiente -----------------
        val ambienteLabel = when (inputs.ambiente) {
            AmbienteType.SECO ->
                res.getString(R.string.calc_rev_amb_seco)

            AmbienteType.SEMI ->
                res.getString(R.string.calc_rev_amb_semi)

            AmbienteType.MOLHADO ->
                res.getString(R.string.calc_rev_amb_molhado)

            AmbienteType.SEMPRE ->
                res.getString(R.string.calc_rev_amb_sempre)

            null -> null
        }

        appendLine(
            res.getString(
                R.string.calc_rev_line_ambiente,
                ambienteLabel ?: res.getString(R.string.calc_rev_value_none)
            )
        )

        // ----------------- Tráfego (Intertravado) -----------------
        if (inputs.revest == RevestimentoType.PISO_INTERTRAVADO && inputs.trafego != null) {

            val trafegoLabel = when (inputs.trafego) {
                TrafegoType.LEVE ->
                    res.getString(R.string.calc_step_trafego_leve)

                TrafegoType.MEDIO ->
                    res.getString(R.string.calc_step_trafego_medio)

                TrafegoType.PESADO ->
                    res.getString(R.string.calc_step_trafego_pesado)
            }

            appendLine(
                res.getString(
                    R.string.calc_rev_line_trafego,
                    trafegoLabel
                )
            )
        }

        // ----------------- Paredes -----------------
        inputs.paredeQtd
            ?.takeIf { it > 0 }
            ?.let { qtd ->
                val label = if (qtd == 1)
                    res.getString(R.string.calc_rev_parede_singular)
                else
                    res.getString(R.string.calc_rev_parede_plural)

                appendLine(
                    res.getString(
                        R.string.calc_rev_line_paredes,
                        label,
                        qtd
                    )
                )
            }

        // ----------------- Área base -----------------
        AreaCalculator.areaBaseM2(inputs)?.let { area ->
            appendLine(
                res.getString(
                    R.string.calc_rev_line_area_total,
                    arred2(area)
                )
            )
        }

        // ----------------- Abertura parede -----------------
        inputs.aberturaM2
            ?.takeIf { it > 0.0 }
            ?.let { abertura ->
                appendLine(
                    res.getString(
                        R.string.calc_rev_line_abertura_parede,
                        arred2(abertura)
                    )
                )
            }

        // ----------------- Dimensão da peça -----------------
        if (inputs.pecaCompCm != null && inputs.pecaLargCm != null) {
            when (inputs.revest) {
                RevestimentoType.MARMORE,
                RevestimentoType.GRANITO -> {
                    val compM = inputs.pecaCompCm / 100.0
                    val largM = inputs.pecaLargCm / 100.0
                    appendLine(
                        res.getString(
                            R.string.calc_rev_line_peca_m,
                            arred2(compM),
                            arred2(largM)
                        )
                    )
                }

                RevestimentoType.PEDRA -> {
                    // Mantém o comportamento atual: não exibe dimensão padrão
                }

                RevestimentoType.PASTILHA -> {
                    appendLine(
                        res.getString(
                            R.string.calc_rev_line_peca_cm,
                            arred2(inputs.pecaCompCm),
                            arred2(inputs.pecaLargCm)
                        )
                    )
                }

                else -> {
                    appendLine(
                        res.getString(
                            R.string.calc_rev_line_peca_cm,
                            arred0(inputs.pecaCompCm),
                            arred0(inputs.pecaLargCm)
                        )
                    )
                }
            }
        }

        // ----------------- Espessura -----------------
        inputs.pecaEspMm?.let { espMm ->
            if (inputs.revest == RevestimentoType.PISO_INTERTRAVADO) {
                val espCm = espMm / 10.0
                appendLine(
                    res.getString(
                        R.string.calc_rev_line_espessura_cm,
                        arred1(espCm)
                    )
                )
            } else {
                appendLine(
                    res.getString(
                        R.string.calc_rev_line_espessura_mm,
                        arred1(espMm)
                    )
                )
            }
        }

        // ----------------- Junta -----------------
        inputs.juntaMm?.let {
            appendLine(
                res.getString(
                    R.string.calc_rev_line_junta,
                    arred2(it)
                )
            )
        }

        // ----------------- Peças por caixa -----------------
        inputs.pecasPorCaixa?.let {
            appendLine(
                res.getString(
                    R.string.calc_rev_line_pecas_caixa,
                    it
                )
            )
        }

        // ----------------- Desnível -----------------
        inputs.desnivelCm?.let {
            appendLine(
                res.getString(
                    R.string.calc_rev_line_desnivel,
                    arred1(it)
                )
            )
        }

        // ----------------- Rodapé (apenas texto de resumo) -----------------
        if (inputs.rodapeEnable &&
            RevestimentoSpecifications.hasRodapeStep(inputs) &&
            inputs.rodapeAlturaCm != null
        ) {
            appendRodapeResumo(context, this, inputs)
        }

        if (inputs.rodapeEnable && RevestimentoSpecifications.hasRodapeStep(inputs)) {
            inputs.rodapeDescontarVaoM
                .takeIf { it > 0.0 }
                ?.let { aberturaRodape ->
                    appendLine(
                        res.getString(
                            R.string.calc_rev_line_abertura_rodape,
                            arred2(aberturaRodape)
                        )
                    )
                }
        }

        // ----------------- Impermeabilização -----------------
        if (inputs.impermeabilizacaoOn) {
            appendLine(res.getString(R.string.calc_rev_line_impermeabilizacao))
        }

        // ----------------- Sobra técnica -----------------
        if (inputs.sobraPct != null && inputs.sobraPct >= 0) {
            append(
                res.getString(
                    R.string.calc_rev_line_sobra_tecnica,
                    arred2(inputs.sobraPct)
                )
            )
        }
    }

    private fun appendRodapeResumo(
        context: Context,
        sb: StringBuilder,
        inputs: Inputs
    ) {
        val res = context.resources
        val perimetro = RodapeCalculator.rodapePerimetroM(inputs) ?: return
        val alturaCm = inputs.rodapeAlturaCm ?: return
        val alturaM = alturaCm / 100.0
        val areaM2 = perimetro * alturaM

        if (inputs.rodapeMaterial == RodapeMaterial.PECA_PRONTA) {
            sb.appendLine(
                res.getString(
                    R.string.calc_rev_line_rodape_peca_pronta,
                    arred2(areaM2)
                )
            )
        } else {
            val areaBaseM2 = RodapeCalculator.rodapeAreaBaseExibicaoM2(inputs)
            sb.appendLine(
                res.getString(
                    R.string.calc_rev_line_rodape_mesma_peca,
                    arred2(areaBaseM2),
                    arred1(alturaCm),
                    arred2(areaM2)
                )
            )
        }
    }

    // ----------------- Helpers de arredondamento (copiados do ViewModel) -----------------
    private fun arred0(v: Double) = round(v)
    private fun arred1(v: Double) = round(v * 10.0) / 10.0
    private fun arred2(v: Double) = round(v * 100.0) / 100.0
}