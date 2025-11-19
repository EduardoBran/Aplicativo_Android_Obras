package com.luizeduardobrandao.obra.ui.calculo.ui

import android.graphics.Canvas
import android.content.Context
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.AreaCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.RodapeCalculator
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter

/**
 * Monta o texto da tela de Revisão de Parâmetros usando strings.xml
 *
 * Nenhuma regra de cálculo é alterada – apenas formatação de UI.
 */
object ReviewSummaryFormatter {

    fun buildResumoRevisao(context: Context, inputs: Inputs): CharSequence {
        val res = context.resources

        // Primeiro monta o texto "normal" (string simples)
        val plainText = buildString {

            // ----------------- Revestimento -----------------
            val revestText = when (inputs.revest) {
                RevestimentoType.PISO -> {
                    if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                        res.getString(R.string.calc_rev_revest_piso_porcelanato)
                    } else {
                        res.getString(R.string.calc_rev_revest_piso_ceramico)
                    }
                }

                RevestimentoType.AZULEJO -> {
                    if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                        res.getString(R.string.calc_rev_revest_azulejo_porcelanato)
                    } else {
                        res.getString(R.string.calc_rev_revest_azulejo_ceramico)
                    }
                }

                RevestimentoType.PASTILHA -> {
                    if (inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO) {
                        res.getString(R.string.calc_rev_revest_pastilha_porcelanato)
                    } else {
                        res.getString(R.string.calc_rev_revest_pastilha_ceramico)
                    }
                }

                RevestimentoType.PEDRA ->
                    res.getString(R.string.calc_rev_revest_pedra)

                RevestimentoType.PISO_INTERTRAVADO ->
                    res.getString(R.string.calc_rev_revest_piso_intertravado)

                RevestimentoType.MARMORE -> when (inputs.aplicacao) {
                    AplicacaoType.PISO ->
                        res.getString(R.string.calc_rev_revest_marmore_piso)

                    AplicacaoType.PAREDE ->
                        res.getString(R.string.calc_rev_revest_marmore_parede)

                    null ->
                        // fallback se por algum motivo ainda não tiver aplicação definida
                        res.getString(R.string.calc_rev_revest_marmore)
                }

                RevestimentoType.GRANITO -> when (inputs.aplicacao) {
                    AplicacaoType.PISO ->
                        res.getString(R.string.calc_rev_revest_granito_piso)

                    AplicacaoType.PAREDE ->
                        res.getString(R.string.calc_rev_revest_granito_parede)

                    null ->
                        res.getString(R.string.calc_rev_revest_granito)
                }

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
                        NumberFormatter.arred2(area)
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
                            NumberFormatter.arred2(abertura)
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
                                NumberFormatter.arred2(compM),
                                NumberFormatter.arred2(largM)
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
                                NumberFormatter.arred2(inputs.pecaCompCm),
                                NumberFormatter.arred2(inputs.pecaLargCm)
                            )
                        )
                    }

                    else -> {
                        appendLine(
                            res.getString(
                                R.string.calc_rev_line_peca_cm,
                                NumberFormatter.arred0(inputs.pecaCompCm),
                                NumberFormatter.arred0(inputs.pecaLargCm)
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
                            NumberFormatter.arred1(espCm)
                        )
                    )
                } else {
                    appendLine(
                        res.getString(
                            R.string.calc_rev_line_espessura_mm,
                            NumberFormatter.arred1(espMm)
                        )
                    )
                }
            }

            // ----------------- Junta -----------------
            inputs.juntaMm?.let {
                appendLine(
                    res.getString(
                        R.string.calc_rev_line_junta,
                        NumberFormatter.arred2(it)
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
                        NumberFormatter.arred1(it)
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
                                NumberFormatter.arred2(aberturaRodape)
                            )
                        )
                    }
            }

            // ----------------- Sobra técnica -----------------
            inputs.sobraPct
                ?.takeIf { it >= 0 }
                ?.let { sobra ->
                    val sobraFmt = NumberFormatter.format(sobra)
                    append(
                        res.getString(
                            R.string.calc_rev_line_sobra_tecnica,
                            sobraFmt
                        )
                    )
                }
        }

        // Agora aplica o "bullet maior" usando Span
        return applyBigIconCharSpan(context, plainText)
    }

    private fun applyBigIconCharSpan(context: Context, text: String): CharSequence {
        val spannable = SpannableStringBuilder(text)
        val iconChar = '•'

        val scale = 1.4f // tamanho do ícone
        val color = ContextCompat.getColor(context, R.color.icon_review_summary)

        var index = text.indexOf(iconChar)
        while (index >= 0) {
            spannable.setSpan(
                CenteredIconChar(color, scale),
                index,
                index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = text.indexOf(iconChar, index + 1)
        }

        return spannable
    }

    private class CenteredIconChar(
        @ColorInt private val color: Int,
        private val relativeSize: Float
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val p = Paint(paint)
            p.textSize = paint.textSize * relativeSize
            // largura suficiente para desenhar o "•" maior
            return p.measureText("•").toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            baseline: Int,
            bottom: Int,
            paint: Paint
        ) {
            val oldColor = paint.color
            val oldSize = paint.textSize

            // Métricas do texto normal (antes de aumentar o tamanho)
            val fmOrig = paint.fontMetricsInt
            val centerOrig = baseline + (fmOrig.ascent + fmOrig.descent) / 2f

            // Configura o paint pro ícone maior
            paint.color = color
            paint.textSize = oldSize * relativeSize

            // Métricas do ícone maior
            val fmBig = paint.fontMetricsInt
            val centerBigOffset = (fmBig.ascent + fmBig.descent) / 2f

            // Baseline do ícone para que o CENTRO dele coincida com o centro da linha original
            val bulletBaseline = centerOrig - centerBigOffset

            canvas.drawText("•", x, bulletBaseline, paint)

            // Restaura o paint
            paint.color = oldColor
            paint.textSize = oldSize
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
                    NumberFormatter.arred2(areaM2)
                )
            )
        } else {
            val areaBaseM2 = RodapeCalculator.rodapeAreaBaseExibicaoM2(inputs)
            sb.appendLine(
                res.getString(
                    R.string.calc_rev_line_rodape_mesma_peca,
                    NumberFormatter.arred2(areaBaseM2),
                    NumberFormatter.arred1(alturaCm),
                    NumberFormatter.arred2(areaM2)
                )
            )
        }
    }
}