package com.luizeduardobrandao.obra.ui.levelmeter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

class LevelOverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val pGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = "#2ECC71".toColorInt() // linha verde fixa
    }

    private val pRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = "#E74C3C".toColorInt() // linha vermelha móvel
    }

    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }

    private val pDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000 // leve escurecido pra legibilidade
    }

    // Reusáveis para evitar alocação em onDraw
    private val textBounds = Rect()
    private val textBgRect = RectF()
    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA000000.toInt() }

    // Cache de dimensões para não recomputar sempre
    private var wF = 0f
    private var hF = 0f
    private var cx = 0f
    private var cy = 0f
    private var pad = 16f

    /** Ângulo atual (°), -90..+90. */
    var rollDeg: Float = 0f
        set(value) {
            if (!isLocked) {
                field = value.coerceIn(-90f, 90f)
                displayDeg = field
                postInvalidateOnAnimation()
            }
        }

    /** Quando travado, congela o valor mostrado. */
    private var displayDeg: Float = 0f

    var isLocked: Boolean = false
        set(value) {
            field = value
            if (value) displayDeg = rollDeg
            postInvalidateOnAnimation()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        wF = w.toFloat()
        hF = h.toFloat()
        cx = wF / 2f
        cy = hF / 2f
        // Ajusta tamanho do texto proporcional ao menor lado
        pText.textSize = min(wF, hF) * 0.045f
        // Padding do “cartucho” do texto (opcionalmente proporcional)
        pad = min(wF, hF) * 0.02f
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        // Fundo levemente dim
        c.drawRect(0f, 0f, wF, hF, pDim)

        // Cruz verde fixa
        c.drawLine(0f, cy, wF, cy, pGreen)
        c.drawLine(cx, 0f, cx, hF, pGreen)

        // Linhas vermelhas rotacionadas
        c.withTranslation(cx, cy) {
            rotate(displayDeg)
            val len = maxOf(wF, hF)

            // Linha vermelha horizontal
            drawLine(-len, 0f, len, 0f, pRed)

            // Linha vermelha vertical
            drawLine(0f, -len, 0f, len, pRed)
        }

        // Texto: % de inclinação (sem String.format para evitar alocação)
        val pct = abs(displayDeg) / 90f * 100f
        val pct10 = round(pct * 10f) / 10f // 1 casa
        val txt = buildPercent(pct10)      // e.g. "12.3%"

        // Medidas do texto (reuso de Rect)
        pText.getTextBounds(txt, 0, txt.length, textBounds)

        val tx = cx + (12f)
        val ty = cy - (12f)

        // Fundo arredondado atrás do texto (reuso de RectF)
        textBgRect.set(
            tx - pad,
            ty - textBounds.height() - pad,
            tx + textBounds.width() + pad,
            ty + pad / 2f
        )
        c.drawRoundRect(textBgRect, 12f, 12f, textBgPaint)

        // Texto
        c.drawText(txt, tx, ty, pText)
    }

    /** Constrói o texto de porcentagem sem alocações pesadas de format/locales. */
    private fun buildPercent(value: Float): String {
        // Garante sempre uma casa decimal
        val intPart = value.toInt()
        val fracPart = ((value - intPart) * 10f).toInt().coerceIn(0, 9)
        return "$intPart.$fracPart%"
    }
}