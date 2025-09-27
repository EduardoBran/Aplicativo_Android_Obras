package com.luizeduardobrandao.obra.ui.cronograma.gantt.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.utils.GanttUtils
import java.time.LocalDate
import kotlin.math.floor

/**
 * Desenha uma linha de Gantt "leve":
 * - Uma coluna por dia do header (fornecido pelo Adapter)
 * - Destaca o intervalo planejado da Etapa [etapaStart -> etapaEnd]
 * - Marca os dias concluídos (quadradinhos)
 * - Toque/toggle em uma coluna → adiciona/remove do set de concluídos e chama callback
 *
 * Largura do dia: 32dp (ajustável); altura: o próprio height do item (ex.: 64dp).
 */
class GanttTimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Config
    private val dayWidthPx = resources.displayMetrics.density * 32f
    private val dayGapPx = resources.displayMetrics.density * 2f
    private val radiusPx = resources.displayMetrics.density * 6f

    private val tmpRect = RectF()

    /** Pinturas **/

    // Desenha as bordas/grade de cada “quadradinho” (célula do dia).
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_surfaceVariant)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density // 1dp
    }

    // Preenche as células que estão dentro do período planejado da etapa (de dataInicio a dataFim).
    private val plannedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_secondaryContainer)
        style = Paint.Style.FILL
    }

    // Preenche as células dos dias concluídos (os “quadradinhos marcados”).
    private val donePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.success)
        style = Paint.Style.FILL
    }

    // Desenha a linha vertical de “hoje” por cima da grade, quando a data atual está no cabeçalho.
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.warning)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
    }

    // Dados
    private var headerDays: List<LocalDate> = emptyList()
    private var etapaStart: LocalDate? = null
    private var etapaEnd: LocalDate? = null
    private var doneUtc: MutableSet<String> = mutableSetOf()

    private var onDayToggle: ((Set<String>) -> Unit)? = null

    private val touchSlop by lazy {
        android.view.ViewConfiguration.get(context).scaledTouchSlop
    }
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    fun setHeaderDays(days: List<LocalDate>) {
        headerDays = days
        requestLayout()
        invalidate()
    }

    fun setEtapaRange(start: LocalDate?, end: LocalDate?) {
        etapaStart = start
        etapaEnd = end
        invalidate()
    }

    fun setDiasConcluidosUtc(set: Set<String>) {
        doneUtc = set.toMutableSet()
        invalidate()
    }

    fun setOnDayToggleListener(cb: (Set<String>) -> Unit) {
        onDayToggle = cb
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = ((dayWidthPx + dayGapPx) * headerDays.size).toInt()
        val desiredHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (headerDays.isEmpty()) return

        val h = height.toFloat()
        val top = resources.displayMetrics.density * 6f
        val bottom = h - resources.displayMetrics.density * 6f
        val today = LocalDate.now()

        headerDays.forEachIndexed { idx, day ->
            val left = idx * (dayWidthPx + dayGapPx)
            val right = left + dayWidthPx
            tmpRect.set(left, top, right, bottom)

            val isSunday = GanttUtils.isSunday(day)

            val inPlanned = (!isSunday && etapaStart != null && etapaEnd != null &&
                    !day.isBefore(etapaStart) && !day.isAfter(etapaEnd))
            if (inPlanned) canvas.drawRoundRect(tmpRect, radiusPx, radiusPx, plannedPaint)

            val utc = GanttUtils.localDateToUtcString(day)
            if (!isSunday && utc in doneUtc) {
                canvas.drawRoundRect(tmpRect, radiusPx, radiusPx, donePaint)
            }

            // borda sempre
            canvas.drawRoundRect(tmpRect, radiusPx, radiusPx, gridPaint)
        }

        val todayIdx = headerDays.indexOf(today)
        if (todayIdx >= 0) {
            val lx = todayIdx * (dayWidthPx + dayGapPx) + dayWidthPx * 0.5f
            canvas.drawLine(lx, 0f, lx, h, todayPaint)
        }
    }

    private fun idxFromX(x: Float): Int {
        val col = (dayWidthPx + dayGapPx)
        var idx = floor(x / col).toInt()
        if (idx < 0) idx = 0
        if (idx >= headerDays.size) idx = headerDays.lastIndex
        return idx
    }

    private fun dateAt(idx: Int): LocalDate? =
        if (idx in headerDays.indices) headerDays[idx] else null

    private fun isWithinEtapa(day: LocalDate): Boolean {
        if (GanttUtils.isSunday(day)) return false
        val s = etapaStart ?: return false
        val e = etapaEnd ?: return false
        return !day.isBefore(s) && !day.isAfter(e)
    }

    private fun toggleByIndex(idx: Int) {
        val day = dateAt(idx) ?: return
        if (!isWithinEtapa(day)) return
        val utc = GanttUtils.localDateToUtcString(day)
        if (doneUtc.contains(utc)) doneUtc.remove(utc) else doneUtc.add(utc)
        onDayToggle?.invoke(doneUtc.toSet()) // devolve uma cópia imutável
        invalidate()
    }

    // --- acessibilidade (recomendado quando intercepta touch) ---
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // --- tocar/arrastar para marcar vários dias ---
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // não bloqueie o ScrollView ainda
                downX = ev.x
                downY = ev.y
                moved = false
                // retorne false? Não. Retorne true para continuar recebendo UP.
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(ev.x - downX)
                val dy = kotlin.math.abs(ev.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    moved = true
                }
                // NÃO consome o MOVE -> deixa o HorizontalScrollView rolar
                return false
            }

            MotionEvent.ACTION_UP -> {
                // se não houve movimento significativo, trate como "tap"
                val dx = kotlin.math.abs(ev.x - downX)
                val dy = kotlin.math.abs(ev.y - downY)
                if (!moved && dx <= touchSlop && dy <= touchSlop) {
                    val idx = idxFromX(ev.x)
                    toggleByIndex(idx)
                    performClick()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                moved = false
                return false
            }
        }
        return false
    }
}