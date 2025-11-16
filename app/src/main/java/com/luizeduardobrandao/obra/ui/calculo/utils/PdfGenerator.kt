package com.luizeduardobrandao.obra.ui.calculo.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel
import java.io.ByteArrayOutputStream

/**
 * Gerador de PDF para relatório de cálculo de revestimento
 */
class PdfGenerator(
    private val context: Context,
    private val buildComprarCell: (CalcRevestimentoViewModel.MaterialItem) -> String
) {

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val LEFT_MARGIN = 34
        const val RIGHT_MARGIN = 34
        const val LINE_GAP = 14
    }

    private val titlePaint = Paint().apply {
        isAntiAlias = true
        textSize = 18f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 12f
        typeface = Typeface.SANS_SERIF
    }

    private val linePaint = Paint().apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 1f
    }

    /**
     * Gera PDF completo a partir do resultado do cálculo
     */
    fun generate(
        resultado: CalcRevestimentoViewModel.Resultado,
        pecaEspMm: Double?,
        pecasPorCaixa: Int?,
        desnivelCm: Double?
    ): ByteArray {
        val doc = PdfDocument()
        var pageNum = 1
        var y = 60

        val right = PAGE_WIDTH - RIGHT_MARGIN

        fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            val page = doc.startPage(info)
            y = 60

            // Título
            page.canvas.drawText(
                context.getString(R.string.calc_title),
                LEFT_MARGIN.toFloat(),
                y.toFloat(),
                titlePaint
            )
            y += LINE_GAP + 4

            // Header
            val headerText = buildHeaderText(resultado, pecaEspMm)
            val used = drawMultiline(page, headerText, y)
            y += used

            // Espessura, desnível e peças por caixa (se informadas)
            pecaEspMm?.let { esp ->
                page.canvas.drawText(
                    "Espessura: ${NumberFormatter.format(esp)} mm",
                    LEFT_MARGIN.toFloat(),
                    y.toFloat(),
                    textPaint
                )
                y += LINE_GAP
            }

            desnivelCm?.let { d ->
                page.canvas.drawText(
                    "Desnível: ${NumberFormatter.format(d)} cm",
                    LEFT_MARGIN.toFloat(),
                    y.toFloat(),
                    textPaint
                )
                y += LINE_GAP
            }

            pecasPorCaixa?.let { ppc ->
                page.canvas.drawText(
                    "Peças por caixa: $ppc",
                    LEFT_MARGIN.toFloat(),
                    y.toFloat(),
                    textPaint
                )
                y += LINE_GAP
            }

            // Linha separadora
            val lineY = y + 4
            page.canvas.drawLine(
                LEFT_MARGIN.toFloat(),
                lineY.toFloat(),
                right.toFloat(),
                lineY.toFloat(),
                linePaint
            )

            // Espaço abaixo da linha antes da frase
            y = lineY + LINE_GAP

            // Frase informativa antes da tabela
            val sobraStr = NumberFormatter.format(resultado.header.sobraPct)
            val infoText =
                "Os valores abaixo já consideram a sobra técnica de $sobraStr% informada."
            page.canvas.drawText(
                infoText,
                LEFT_MARGIN.toFloat(),
                y.toFloat(),
                textPaint
            )

            // Espaço após a frase
            y += LINE_GAP

            return page
        }

        var page = newPage()

        // Tabela
        fun drawRow(c1: String, c2: String, c3: String, bold: Boolean = false) {
            val colW = (right - LEFT_MARGIN)

            // 3 colunas: Material | Qtd | Comprar
            val w1 = (colW * 0.5f).toInt()  // Material
            val w2 = (colW * 0.18f).toInt() // Qtd
            val x1 = LEFT_MARGIN
            val x2 = LEFT_MARGIN + w1 + 8
            val x3 = LEFT_MARGIN + w1 + w2 + 16
            val w3 = right - x3          // largura efetiva da coluna "Comprar"

            val paint = Paint(textPaint).apply {
                typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }

            // Métricas reais da fonte
            val fm = paint.fontMetrics
            val textHeight = fm.descent - fm.ascent

            // Quebra por coluna: cada uma respeita sua própria largura
            val l1 = wrapText(c1, paint, w1.toFloat())
            val l2 = wrapText(c2, paint, w2.toFloat())
            val l3 = wrapText(c3, paint, w3.toFloat())

            val maxLines = maxOf(l1.size, l2.size, l3.size)

            // Espaço interno acima/abaixo do texto
            val innerPadding = 6

            // Altura total da linha (inclui texto + paddings + linhas extras)
            val rowHeight = (innerPadding * 2 + textHeight + (maxLines - 1) * LINE_GAP).toInt()

            // Quebra de página considerando a linha inteira
            if (y + rowHeight > PAGE_HEIGHT - 40) {
                doc.finishPage(page)
                pageNum++
                page = newPage()
            }

            // Baseline da primeira linha, centralizada verticalmente
            val firstBaseline = y + innerPadding - fm.ascent

            fun drawColumn(
                lines: List<String>,
                startX: Int,
                colWidth: Int,
                alignRight: Boolean = false
            ) {
                var baseline = firstBaseline
                lines.forEach { line ->
                    val x = if (alignRight) {
                        val textWidth = paint.measureText(line)
                        // encosta na borda direita da coluna (que no caso da última é o 'right')
                        startX + colWidth - textWidth
                    } else {
                        startX.toFloat()
                    }
                    page.canvas.drawText(line, x, baseline, paint)
                    baseline += LINE_GAP
                }
            }

            // Desenha colunas: Material | Qtd | Comprar
            drawColumn(l1, x1, w1)                  // Material (esquerda)
            drawColumn(l2, x2, w2)                  // Qtd (esquerda)
            drawColumn(l3, x3, w3, alignRight = true) // Comprar (alinhado à direita)


            // Linha inferior da linha da tabela
            val bottomY = y + rowHeight
            page.canvas.drawLine(
                LEFT_MARGIN.toFloat(),
                bottomY.toFloat(),
                right.toFloat(),
                bottomY.toFloat(),
                linePaint
            )

            // Próxima linha
            y = bottomY
        }

        // Cabeçalho da tabela
        drawRow(
            context.getString(R.string.col_item),
            context.getString(R.string.col_qtd),
            context.getString(R.string.col_comprar),
            bold = true
        )

        // Linhas de dados
        // Linhas de dados
        resultado.itens.forEach { item ->
            // Qtd exatamente como no app: valor + unidade juntos
            val qtdStr = run {
                val valor = NumberFormatter.format(item.qtd)
                val unidade = item.unid.trim()
                if (unidade.isNotEmpty()) "$valor$unidade" else valor
            }

            drawRow(
                item.item,
                qtdStr,
                buildComprarCell(item)
            )
        }

        doc.finishPage(page)

        return try {
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            out.toByteArray()
        } finally {
            doc.close()
        }
    }

    /**
     * Constrói texto do cabeçalho
     */
    private fun buildHeaderText(
        r: CalcRevestimentoViewModel.Resultado,
        pecaEspMm: Double?
    ): String {
        val h = r.header
        val areaOriginalHeader = h.areaM2
        val espessuraMm = pecaEspMm ?: 8.0

        fun mapRevest(tipo: String?): String = when (tipo) {
            "PISO" -> "Piso"
            "AZULEJO" -> "Azulejo"
            "PASTILHA" -> "Pastilha"
            "PEDRA" -> "Pedra Portuguesa"
            "PISO_INTERTRAVADO" -> "Piso intertravado"
            "MARMORE" -> "Mármore"
            "GRANITO" -> "Granito"
            else -> tipo.orEmpty()
        }

        fun mapAmbiente(amb: String?): String = when (amb) {
            "SECO" -> "Seco"
            "SEMI" -> "Semi-molhado"
            "MOLHADO" -> "Molhado"
            "SEMPRE" -> "Sempre molhado"
            else -> amb.orEmpty()
        }

        fun mapTrafego(traf: String?): String {
            val res = context.resources
            return when (traf) {
                "LEVE" -> res.getString(R.string.calc_step_trafego_leve)
                "MEDIO" -> res.getString(R.string.calc_step_trafego_medio)
                "PESADO" -> res.getString(R.string.calc_step_trafego_pesado)
                else -> traf.orEmpty()
            }
        }

        var revestStr = mapRevest(h.tipo)
        val ambienteStr = mapAmbiente(h.ambiente)
        val trafegoStr = mapTrafego(h.trafego)

        // Ajuste específico para Piso: cerâmico x porcelanato no cabeçalho
        if (h.tipo == "PISO") {
            val pisoItem = r.itens.firstOrNull {
                it.item.startsWith("Piso porcelanato", ignoreCase = true) ||
                        it.item.startsWith("Piso cerâmico", ignoreCase = true)
            }

            revestStr = when {
                pisoItem?.item?.startsWith("Piso porcelanato", ignoreCase = true) == true ->
                    "Piso: Porcelanato"

                pisoItem?.item?.startsWith("Piso cerâmico", ignoreCase = true) == true ->
                    "Piso: Cerâmico"

                else -> "Piso"
            }
        }

        // Texto do rodapé
        val rodapeStr = run {
            // Detecta Rodapé (peça pronta) a partir dos itens
            val rodapePecaPronta = r.itens.firstOrNull {
                it.item.equals("Rodapé", ignoreCase = true) &&
                        (it.observacao?.contains("peça pronta", ignoreCase = true) == true)
            }

            when {
                // Caso Rodapé (peça pronta): igual ao card de revisão
                rodapePecaPronta != null ->
                    "Rodapé: ${NumberFormatter.format(rodapePecaPronta.qtd)} m² (peça pronta)"

                // Demais casos (mantém comportamento atual)
                h.rodapeAreaM2 > 0 ->
                    "Rodapé: ${
                        NumberFormatter.format(h.rodapeBaseM2)
                    } m² + ${
                        h.rodapeAlturaCm
                    } cm = ${
                        NumberFormatter.format(h.rodapeAreaM2)
                    } m²"

                else -> "Sem rodapé"
            }
        }

        return if (h.tipo == "PISO_INTERTRAVADO") {
            buildString {
                append(revestStr)
                append(" | Tipo do Ambiente: ")
                append(ambienteStr)
                append(" | Tipo do Tráfego: ")
                append(trafegoStr)
                append(" | Área original: ")
                append(NumberFormatter.format(areaOriginalHeader))
                append(" m² | ")
                append(rodapeStr)
                append(" | Espessura: ")
                append(NumberFormatter.format(espessuraMm))
                append(" | Sobra técnica: ")
                append(NumberFormatter.format(h.sobraPct))
                append("%")
            }
        } else {
            buildString {
                append(revestStr)
                append(" | Tipo do Ambiente: ")
                append(ambienteStr)
                append(" | Área original: ")
                append(NumberFormatter.format(areaOriginalHeader))
                append(" m² | ")
                append(rodapeStr)
                append(" | Junta ")
                append(NumberFormatter.format(h.juntaMm))
                append(" mm | Espessura: ")
                append(NumberFormatter.format(espessuraMm))
                append("mm | Sobra Técnica: ")
                append(NumberFormatter.format(h.sobraPct))
                append("%")
            }
        }
    }

    /**
     * Desenha texto multilinha
     */
    private fun drawMultiline(page: PdfDocument.Page, text: String, startY: Int): Int {
        val right = PAGE_WIDTH - RIGHT_MARGIN
        val lines = wrapText(text, textPaint, (right - LEFT_MARGIN).toFloat())
        var currentY = startY

        for (line in lines) {
            page.canvas.drawText(line, LEFT_MARGIN.toFloat(), currentY.toFloat(), textPaint)
            currentY += LINE_GAP
        }

        return lines.size * LINE_GAP
    }

    /**
     * Quebra texto em linhas que cabem na largura especificada
     */
    @Suppress("SameParameterValue")
    private fun wrapText(text: String, paint: Paint, width: Float): List<String> {
        if (text.isBlank()) return listOf("")

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val current = StringBuilder()

        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= width) {
                current.clear()
                current.append(test)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current.clear()
                current.append(word)
            }
        }

        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}