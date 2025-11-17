package com.luizeduardobrandao.obra.ui.calculo.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.RadioGroup
import androidx.core.view.isGone
import com.luizeduardobrandao.obra.R

/**
 * RadioGroup que organiza os RadioButtons em até 3 colunas por linha.
 *
 * - Mantém seleção exclusiva (RadioGroup normal)
 * - Modo 1 linha: distribui 3 botões com "gap" fixo entre eles, centralizando o bloco
 * - Modo 3 linhas: grade 3xN com colunas alinhadas
 * - Quem centraliza o bloco na tela é o layout pai (layout_gravity)
 */
class ThreeColumnRadioGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RadioGroup(context, attrs) {

    private val columns = 3

    // Carrega tamanho espaçamento horizontal do Dimens
    private val horizontalGapPxDimen =
        resources.getDimensionPixelSize(R.dimen.rg_three_column_gap_horizontal)

    // Espaçamentos "atuais" (podem ser ajustados conforme o número de linhas)
    private var horizontalSpacingPx = horizontalGapPxDimen
    private var verticalSpacingPx = dpToPx(4f)

    // largura fixa de cada "célula" e altura da linha (modo grade)
    private var cellWidth = 0
    private var rowHeight = 0

    // quantidade de linhas da grade
    private var rows = 0

    private fun dpToPx(dp: Float): Int {
        val dens = resources.displayMetrics.density
        return (dp * dens + 0.5f).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visibleChildren = (0 until childCount)
            .map { getChildAt(it) }
            .filter { !it.isGone }

        if (visibleChildren.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // Medimos cada filho "livre" para descobrir o tamanho natural
        val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        var maxChildWidth = 0
        var maxChildHeight = 0

        for (child in visibleChildren) {
            child.measure(childWidthSpec, childHeightSpec)
            maxChildWidth = maxOf(maxChildWidth, child.measuredWidth)
            maxChildHeight = maxOf(maxChildHeight, child.measuredHeight)
        }

        cellWidth = maxChildWidth
        rowHeight = maxChildHeight

        rows = (visibleChildren.size + columns - 1) / columns

        // 👉 padding "invisível" que não deve entrar na largura visual
        val first = visibleChildren.first()
        val last = visibleChildren.last()
        val firstStartPadding = first.paddingStart
        val lastEndPadding = last.paddingEnd

        val gridWidth: Int
        val gridHeight: Int

        if (rows == 1) {
            // MODO 1 LINHA: gap fixo entre os 3 botões
            horizontalSpacingPx = horizontalGapPxDimen
            verticalSpacingPx = 0

            val totalChildrenWidth =
                visibleChildren.sumOf { it.measuredWidth } - firstStartPadding - lastEndPadding
            val gapsTotal = horizontalSpacingPx * (visibleChildren.size - 1)

            val contentWidth = totalChildrenWidth + gapsTotal
            gridWidth = contentWidth
            gridHeight = rowHeight
        } else {
            // MODO GRADE 3xN: colunas alinhadas, mas largura visual
            horizontalSpacingPx = horizontalGapPxDimen
            verticalSpacingPx = dpToPx(8f)

            val rawGridWidth = columns * cellWidth + (columns - 1) * horizontalSpacingPx

            // Tira o padding inútil do primeiro/último
            gridWidth = rawGridWidth - firstStartPadding - lastEndPadding
            gridHeight = rows * rowHeight + (rows - 1) * verticalSpacingPx
        }

        val desiredWidth = paddingLeft + gridWidth + paddingRight
        val desiredHeight = paddingTop + gridHeight + paddingBottom

        val finalWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val finalHeight = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(finalWidth, finalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (rows == 1) {
            layoutSingleRow()
        } else {
            layoutGrid()
        }
    }

    /**
     * Layout especial para 1 linha:
     * distribui os botões com "gap" fixo entre eles e centraliza o bloco.
     */
    private fun layoutSingleRow() {
        val visibleChildren = (0 until childCount)
            .map { getChildAt(it) }
            .filter { !it.isGone }

        if (visibleChildren.isEmpty()) return

        val gap = horizontalSpacingPx

        val first = visibleChildren.first()
        val last = visibleChildren.last()

        val firstStartPadding = first.paddingStart
        val lastEndPadding = last.paddingEnd

        val totalChildrenWidth =
            visibleChildren.sumOf { it.measuredWidth } - firstStartPadding - lastEndPadding
        val gapsTotal = gap * (visibleChildren.size - 1)
        val contentWidth = totalChildrenWidth + gapsTotal

        val freeSpace = measuredWidth - paddingLeft - paddingRight - contentWidth

        // Centraliza do centro da bolinha inicial até o fim do último texto
        var x = paddingLeft + freeSpace / 2 - firstStartPadding
        val centerY = paddingTop + rowHeight / 2

        for (child in visibleChildren) {
            val w = child.measuredWidth
            val h = child.measuredHeight

            val top = centerY - h / 2
            val left = x
            val right = left + w
            val bottom = top + h

            child.layout(left, top, right, bottom)

            x += w + gap
        }
    }


    /**
     * Layout padrão em grade 3xN com colunas alinhadas.
     */
    private fun layoutGrid() {
        val visibleChildren = (0 until childCount)
            .map { getChildAt(it) }
            .filter { !it.isGone }

        if (visibleChildren.isEmpty()) return

        // Mesmo truque: queremos que a borda esquerda "visual" comece na bolinha
        val first = visibleChildren.first()
        val firstStartPadding = first.paddingStart

        // Começa um pouco antes, compensando o paddingStart do primeiro
        val startX = paddingLeft - firstStartPadding

        var row = 0
        var indexInRow = 0

        for (child in visibleChildren) {
            val col = indexInRow % columns
            if (indexInRow != 0 && col == 0) {
                row++
            }

            val childW = child.measuredWidth
            val childH = child.measuredHeight

            val cellLeft = startX + col * (cellWidth + horizontalSpacingPx)
            val baseTop = paddingTop + row * (rowHeight + verticalSpacingPx)

            val top = baseTop + (rowHeight - childH) / 2
            val right = cellLeft + childW
            val bottom = top + childH

            child.layout(cellLeft, top, right, bottom)

            indexInRow++
        }
    }
}