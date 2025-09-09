package com.luizeduardobrandao.obra.utils

import android.content.Context
import android.content.res.Configuration
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.luizeduardobrandao.obra.R


// ────────────────────────────────────────────────────────────────────────────────
// Grade responsiva (alinhada) - Função (2×3 → fallback 3×2) e Pagamento (2×2)
// Ambas retornam o menor tamanho de texto (sp) realmente usado.
// ────────────────────────────────────────────────────────────────────────────────

/** Função: tenta 2×3; se não couber, fallback 3×2. Retorna o menor sp utilizado. */
fun ensureAlignedTwoRowsOrFallbackToThreeRows(
    context: Context,
    rgRow1: RadioGroup,
    rgRow2: RadioGroup,
    checkBoxes: List<MaterialCheckBox>,
    normalTextSp: Float = 16f,
    minTextSp: Float = 12f,
    colGapDp: Int = 12,
    rowTopGapDp: Int = 6
): Float {
    if (checkBoxes.size != 6) return normalTextSp

    val hGapPx = colGapDp.dpToPx(context)

    // 1) Nunca quebrar; se precisar, vamos encolher
    checkBoxes.forEach {
        it.setSingleLine(true)
        it.maxLines = 1
        it.ellipsize = null
        it.setTextSize(TypedValue.COMPLEX_UNIT_SP, normalTextSp)
    }

    val rowWidth = targetGridWidthPx(rgRow1, context)
    val colWidth2x3 = ((rowWidth - hGapPx * 2) / 3).coerceAtLeast(0)

    fun applyFixedWidth2x3(row: RadioGroup) {
        row.gravity = Gravity.CENTER
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i) as? MaterialCheckBox ?: continue
            val lp = (child.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = colWidth2x3
                marginStart = if (i != 0) hGapPx else 0
            }
            child.layoutParams = lp
        }
    }
    applyFixedWidth2x3(rgRow1)
    enforceWrapAndCenter(rgRow1)

    applyFixedWidth2x3(rgRow2)
    enforceWrapAndCenter(rgRow2)

    // 2) Encolhe para 2×3
    var minSpUsed = normalTextSp
    var stillWrap = false
    checkBoxes.forEach { cb ->
        val used = shrinkUntilSingleLineTextView(cb, colWidth2x3, minTextSp)
        if (used < minSpUsed) minSpUsed = used
        val wraps =
            willTextWrapWidth(cb, used, colWidth2x3 - cb.totalPaddingLeft - cb.totalPaddingRight)
        if (wraps) stillWrap = true
    }
    checkBoxes.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, minSpUsed) }
    if (!stillWrap) return minSpUsed

    // 3) FALLBACK 3×2
    val parent = rgRow1.parent as? LinearLayout ?: return minSpUsed
    val row3 = ensureThirdRowForFunc(parent, rowTopGapDp)

    fun addPair(row: RadioGroup, left: MaterialCheckBox, right: MaterialCheckBox) {
        row.removeAllViews()
        row.gravity = Gravity.CENTER
        val lpLeft = RadioGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val lpRight = RadioGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = hGapPx }
        row.addView(left, lpLeft)
        row.addView(right, lpRight)
    }
    checkBoxes.forEach { (it.parent as? ViewGroup)?.removeView(it) }
    addPair(rgRow1, checkBoxes[0], checkBoxes[1])
    addPair(rgRow2, checkBoxes[2], checkBoxes[3])
    addPair(row3, checkBoxes[4], checkBoxes[5])

    val rowWidth2 = targetGridWidthPx(rgRow1, context)
    val colWidth2x1 = ((rowWidth2 - hGapPx) / 2).coerceAtLeast(0)

    fun applyFixedWidth2x1(row: RadioGroup) {
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i) as? MaterialCheckBox ?: continue
            val lp = (child.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = colWidth2x1
                marginStart = if (i == 1) hGapPx else 0
            }
            child.layoutParams = lp
        }
    }
    applyFixedWidth2x1(rgRow1)
    enforceWrapAndCenter(rgRow1)

    applyFixedWidth2x1(rgRow2)
    enforceWrapAndCenter(rgRow2)

    applyFixedWidth2x1(row3)
    enforceWrapAndCenter(row3)

    // 4) Encolhe para 3×2
    minSpUsed = normalTextSp
    checkBoxes.forEach { cb ->
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, normalTextSp)
        val used = shrinkUntilSingleLineTextView(cb, colWidth2x1, minTextSp)
        if (used < minSpUsed) minSpUsed = used
    }
    checkBoxes.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, minSpUsed) }
    return minSpUsed
}

/** Pagamento 2×2 alinhado. Retorna o menor sp utilizado. */
fun alignPagamentoTwoByTwo(
    context: Context,
    rowPagto1: LinearLayout,
    rowPagto2: LinearLayout,
    radios: List<MaterialRadioButton>,
    normalTextSp: Float = 16f,
    minTextSp: Float = 12f,
    hGapDp: Int = 16
): Float {
    if (radios.size != 4) return normalTextSp
    val hGapPx = hGapDp.dpToPx(context)

    radios.forEach {
        it.setSingleLine(true)
        it.maxLines = 1
        it.ellipsize = null
        it.setTextSize(TypedValue.COMPLEX_UNIT_SP, normalTextSp)
    }

    val rowWidth = targetGridWidthPx(rowPagto1, context)
    val colWidth = ((rowWidth - hGapPx) / 2).coerceAtLeast(0)

    fun applyFixedWidth(row: LinearLayout) {
        row.gravity = Gravity.CENTER
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i) as? MaterialRadioButton ?: continue
            val lp = (v.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = colWidth
                marginStart = if (i == 1) hGapPx else 0
            }
            v.layoutParams = lp
        }
    }
    applyFixedWidth(rowPagto1)
    enforceWrapAndCenter(rowPagto1)

    applyFixedWidth(rowPagto2)
    enforceWrapAndCenter(rowPagto2)

    var minSpUsed = normalTextSp
    radios.forEach { rb ->
        val used = shrinkUntilSingleLineTextView(rb, colWidth, minTextSp)
        if (used < minSpUsed) minSpUsed = used
    }
    radios.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, minSpUsed) }
    return minSpUsed
}

/* ------------------------------- Helpers ------------------------------- */

private fun ensureThirdRowForFunc(parent: LinearLayout, rowTopGapDp: Int): RadioGroup {
    parent.findViewWithTag<RadioGroup>("rgFuncao3")?.let { return it }
    val row3 = RadioGroup(parent.context).apply {
        tag = "rgFuncao3"
        orientation = RadioGroup.HORIZONTAL
        gravity = Gravity.CENTER
    }
    val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,     // ERA MATCH_PARENT
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = rowTopGapDp.dpToPx(parent.context)
        gravity = Gravity.CENTER_HORIZONTAL
    }

    val radioGroups = parent.childrenOfType<RadioGroup>()
    val rg2Index =
        radioGroups.getOrNull(1)?.let { parent.indexOfChild(it) } ?: (parent.childCount - 1)
    parent.addView(row3, rg2Index + 1, lp)
    return row3
}

/** Reduz até caber em uma linha dentro de [columnWidthPx] e retorna o sp usado. */
private fun shrinkUntilSingleLineTextView(
    tv: TextView,
    columnWidthPx: Int,
    minTextSp: Float
): Float {
    val textAvailable =
        (columnWidthPx - tv.totalPaddingLeft - tv.totalPaddingRight).coerceAtLeast(0)
    var sizeSp = pxToSpView(tv, tv.textSize)
    while (sizeSp > minTextSp && willTextWrapWidth(tv, sizeSp, textAvailable)) sizeSp -= 1f
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    return sizeSp
}

private fun willTextWrapWidth(tv: TextView, sizeSp: Float, textWidthPx: Int): Boolean {
    val tp = TextPaint(tv.paint).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sizeSp, tv.resources.displayMetrics
        )
        typeface = tv.typeface
        isAntiAlias = true
    }
    val text = tv.text?.toString().orEmpty()
    if (text.isEmpty() || textWidthPx <= 0) return false

    // Mede a largura “reta” do texto. Se for maior que a área disponível,
    // *aí sim* consideramos que quebraria.
    val measured = tp.measureText(text)
    return measured > textWidthPx
}

private fun Int.dpToPx(ctx: Context): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        ctx.resources.displayMetrics
    ).toInt()

private fun pxToSpView(view: View, px: Float): Float {
    val dm = view.resources.displayMetrics
    val fs = view.resources.configuration.fontScale
    return px / (dm.density * fs)
}

/** Largura alvo da grade:
 *  - usa a largura disponível do pai
 *  - em landscape limita a um teto para não esticar de ponta a ponta (mantém visual centrado)
 */
private fun targetGridWidthPx(row: ViewGroup, ctx: Context): Int {
    val parent = row.parent as? ViewGroup ?: row
    val avail = parent.measuredWidth - parent.paddingLeft - parent.paddingRight
    val isLand = row.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLand) {
        // teto visual em landscape (ajuste se quiser mais/menos “respiro” lateral)
        val cap = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 640f, ctx.resources.displayMetrics
        ).toInt()
        return avail.coerceAtMost(cap)
    }
    return avail
}

/** Garante que a linha não vire match_parent após aplicarmos larguras fixas nos filhos. */
private fun enforceWrapAndCenter(row: ViewGroup) {
    (row.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.CENTER_HORIZONTAL
        row.layoutParams = lp
        row.requestLayout()
    }
}

private inline fun <reified T : View> ViewGroup.childrenOfType(): List<T> =
    (0 until childCount).mapNotNull { getChildAt(it) as? T }


/**
 * rgStatusEtapa responsivo:
 * 1) Tenta 1 linha (HORIZONTAL, centrado), escolhendo a MAIOR combinação que caiba:
 *    - textSize ∈ [minSp..maxSp] (prioriza sp maior)
 *    - marginStart ∈ [minGapDp..maxGapDp] (prioriza gap maior)
 * 2) Se NÃO couber nem com 12sp + 6dp → 3 linhas (VERTICAL, alinhado à esquerda).
 *
 * Funciona em portrait e landscape (usa a largura-alvo via targetGridWidthPx).
 * Não cria grupos extras → preserva exclusividade do RadioGroup.
 */
fun RadioGroup.ensureResponsiveStatusEtapa(
    minSp: Float = 12f,
    maxSp: Float = 20f,
    minGapDp: Int = 6,
    maxGapDp: Int = 16
) {
    doOnPreDraw {
        val ctx = context

        val rbPend = findViewById<MaterialRadioButton>(R.id.rbStatPend) ?: return@doOnPreDraw
        val rbAnd = findViewById<MaterialRadioButton>(R.id.rbStatAnd) ?: return@doOnPreDraw
        val rbCon = findViewById<MaterialRadioButton>(R.id.rbStatConcl) ?: return@doOnPreDraw

        listOf(rbPend, rbAnd, rbCon).forEach {
            it.setSingleLine(true); it.maxLines = 1; it.ellipsize = null
        }

        fun fitsOneRow(sp: Float, gapDp: Int): Boolean {
            rbPend.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            rbAnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            rbCon.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)

            (rbAnd.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = gapDp.dpToPx(ctx); rbAnd.layoutParams = it
            }
            (rbCon.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = gapDp.dpToPx(ctx); rbCon.layoutParams = it
            }

            val wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            rbPend.measure(wSpec, hSpec); rbAnd.measure(wSpec, hSpec); rbCon.measure(wSpec, hSpec)

            val avail = targetGridWidthPx(this, ctx)
            val total = rbPend.measuredWidth +
                    (rbAnd.layoutParams as ViewGroup.MarginLayoutParams).marginStart + rbAnd.measuredWidth +
                    (rbCon.layoutParams as ViewGroup.MarginLayoutParams).marginStart + rbCon.measuredWidth

            return total <= avail
        }

        // Guarda o resultado como um único Pair (sp, gap)
        var chosen: Pair<Float, Int>? = null

        outer@ for (sp in maxSp.toInt() downTo minSp.toInt()) {
            for (gap in maxGapDp downTo minGapDp) {
                if (fitsOneRow(sp.toFloat(), gap)) {
                    chosen = sp.toFloat() to gap
                    break@outer
                }
            }
        }

        if (chosen != null) {
            // ✅ 1 linha — aplica e centraliza
            val (sp, gap) = chosen
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER

            rbPend.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            rbAnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            rbCon.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)

            val msPx = gap.dpToPx(ctx)
            (rbAnd.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                if (it.marginStart != msPx) {
                    it.marginStart = msPx; rbAnd.layoutParams = it
                }
            }
            (rbCon.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                if (it.marginStart != msPx) {
                    it.marginStart = msPx; rbCon.layoutParams = it
                }
            }
            return@doOnPreDraw
        }

        // ❌ Não coube nem com 12sp + 4dp → 3 linhas (VERTICAL, alinhado à esquerda)
        orientation = RadioGroup.VERTICAL
        gravity = Gravity.START

        (rbAnd.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = 0; rbAnd.layoutParams = it
        }
        (rbCon.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = 0; rbCon.layoutParams = it
        }

        val fallbackSp = 14f
        rbPend.setTextSize(TypedValue.COMPLEX_UNIT_SP, fallbackSp)
        rbAnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, fallbackSp)
        rbCon.setTextSize(TypedValue.COMPLEX_UNIT_SP, fallbackSp)
    }
}