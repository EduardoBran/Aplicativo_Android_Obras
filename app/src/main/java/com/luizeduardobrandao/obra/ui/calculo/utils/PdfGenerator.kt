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

    private val headerPaint = Paint().apply {
        isAntiAlias = true
        textSize = 12f
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
        pecasPorCaixa: Int?
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
            val headerText = buildHeaderText(resultado)
            val used = drawMultiline(page, headerText, y)
            y += used

            // Espessura e peças por caixa (se informadas)
            pecaEspMm?.let { esp ->
                page.canvas.drawText(
                    "Espessura: ${NumberFormatter.format(esp)} mm",
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
            page.canvas.drawLine(
                LEFT_MARGIN.toFloat(),
                (y + 4).toFloat(),
                right.toFloat(),
                (y + 4).toFloat(),
                linePaint
            )
            y += LINE_GAP

            return page
        }

        var page = newPage()

        // Classe da argamassa
        resultado.classeArgamassa?.let {
            val text = context.getString(R.string.calc_pdf_classe_arg, it)
            page.canvas.drawText(text, LEFT_MARGIN.toFloat(), y.toFloat(), headerPaint)
            y += LINE_GAP
        }

        // Tabela
        fun needBreak(extra: Int = 100): Boolean = y > PAGE_HEIGHT - extra

        fun drawRow(c1: String, c2: String, c3: String, c4: String, bold: Boolean = false) {
            if (needBreak()) {
                doc.finishPage(page)
                pageNum++
                page = newPage()
            }

            val colW = (right - LEFT_MARGIN)
            val w1 = (colW * 0.46).toInt()
            val w2 = (colW * 0.12).toInt()
            val w3 = (colW * 0.18).toInt()

            val paint = Paint(textPaint).apply {
                typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }

            page.canvas.drawText(c1, LEFT_MARGIN.toFloat(), y.toFloat(), paint)
            page.canvas.drawText(c2, (LEFT_MARGIN + w1 + 8).toFloat(), y.toFloat(), paint)
            page.canvas.drawText(c3, (LEFT_MARGIN + w1 + w2 + 16).toFloat(), y.toFloat(), paint)
            page.canvas.drawText(
                c4,
                (LEFT_MARGIN + w1 + w2 + w3 + 24).toFloat(),
                y.toFloat(),
                paint
            )

            page.canvas.drawLine(
                LEFT_MARGIN.toFloat(),
                (y + 4).toFloat(),
                right.toFloat(),
                (y + 4).toFloat(),
                linePaint
            )
            y += LINE_GAP
        }

        // Cabeçalho da tabela
        drawRow(
            context.getString(R.string.col_item),
            context.getString(R.string.col_unid),
            context.getString(R.string.col_usado),
            context.getString(R.string.col_comprar),
            bold = true
        )

        // Linhas de dados
        resultado.itens.forEach { item ->
            drawRow(
                item.item,
                item.unid,
                NumberFormatter.format(item.qtd),
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
    private fun buildHeaderText(r: CalcRevestimentoViewModel.Resultado): String {
        val h = r.header
        val areaTotalHeader = h.areaM2 + h.rodapeAreaM2

        return context.getString(
            R.string.calc_header_pdf,
            h.tipo,
            h.ambiente,
            NumberFormatter.format(areaTotalHeader),
            if (h.rodapeAreaM2 > 0) {
                context.getString(
                    R.string.calc_header_rodape_pdf,
                    NumberFormatter.format(h.rodapeBaseM2),
                    h.rodapeAlturaCm,
                    NumberFormatter.format(h.rodapeAreaM2)
                )
            } else {
                context.getString(R.string.calc_header_rodape_none)
            },
            NumberFormatter.format(h.juntaMm),
            NumberFormatter.format(h.sobraPct)
        )
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