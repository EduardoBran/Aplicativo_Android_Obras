package com.luizeduardobrandao.obra.utils

import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.core.view.updatePaddingRelative
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import android.text.TextUtils
import kotlin.math.max
import kotlin.math.roundToInt

// ───────────────────────── Constantes base ─────────────────────────
private const val SW_MAX_DP = 600f   // ponto a partir do qual consideramos "tablet"

// Faixas de tela
private enum class ScreenTier { SMALL, MEDIUM, LARGE }

// Mapeia screenWidthDp -> faixa
private fun currentTier(swDp: Int): ScreenTier = when {
    swDp <= 361 -> ScreenTier.SMALL      // ≤ 361dp
    swDp <= 600 -> ScreenTier.MEDIUM     // 362–600dp
    else -> ScreenTier.LARGE      // > 600dp
}

/** Config genérica para botão. */
data class ButtonSizingConfig(
    val minPaddingHdp: Int = 14,   // padding horizontal mínimo
    val maxPaddingHdp: Int = 18,   // padding horizontal máximo
    val minPaddingVdp: Int = 8,    // padding vertical mínimo
    val maxPaddingVdp: Int = 10,   // padding vertical máximo
    val minTextSp: Int = 12,       // texto mínimo
    val maxTextSp: Int = 22,       // texto máximo
    val stepSp: Int = 1            // granularidade do autosize
)

// ---- Ajustes de grupo (usado pelo grow-shrink para padding vertical dinâmico)
data class GroupTuning(
    val basePadVdp: Int = 8,    // padding vertical base (top/bottom) quando textSize ≈ baseSp
    val baseSp: Int = 16,       // "tamanho-alvo" de referência; acima disso vamos adicionando dp
    val perSpDp: Float = 0.6f,  // quantos dp acrescentar de padding vertical a cada 1sp acima do baseSp
    val minVdp: Int = 8,        // piso do padding vertical
    val maxVdp: Int = 14,        // teto do padding vertical
    val landscapePadVBoostFactor: Float = 1.2f // opcional: +10% no pad V em landscape
)

/**
 * Variante "grow-shrink":
 * - Define padding responsivo como antes
 * - Habilita AutoSize [min e max] e seta o texto inicialmente em maxSp
 *   => o sistema escolherá automaticamente o MAIOR sp que couber (cresce/encolhe).
 * - Use esta versão quando você quiser que o texto aumente se houver espaço.
 */
fun TextView.applyResponsiveButtonSizingGrowShrink(
    config: ButtonSizingConfig = ButtonSizingConfig(),
    tuning: GroupTuning = GroupTuning()
) {
    val res = resources
    val sw = res.configuration.screenWidthDp.coerceAtLeast(1)
    val fs = res.configuration.fontScale.coerceIn(0.85f, 1.6f)

    // ── caps por faixa (iguais ao seu código atual)
    val tier = currentTier(sw)
    val (padHdp, padVdp, tierMaxSpBase) = when (tier) {
        ScreenTier.SMALL -> Triple(config.minPaddingHdp, config.minPaddingVdp, 16)
        ScreenTier.MEDIUM -> {
            val t = ((sw - 362f) / (SW_MAX_DP - 362f)).coerceIn(0f, 1f)
            val padH = lerp(config.minPaddingHdp, config.maxPaddingHdp, t)
            val padV = lerp(config.minPaddingVdp, config.maxPaddingVdp, t)
            val cap = lerp(16, config.maxTextSp, t)
            Triple(padH, padV, cap)
        }

        ScreenTier.LARGE -> Triple(config.maxPaddingHdp, config.maxPaddingVdp, config.maxTextSp)
    }

    // ── intervalo autosize
    val minSp = config.minTextSp
    val maxSp = (tierMaxSpBase + when {
        fs >= 1.4f -> +2
        fs >= 1.2f -> +1
        else -> 0
    }).coerceAtLeast(minSp).coerceAtMost(config.maxTextSp)

    // ── aplica padding horizontal/vertical base
    val padH = (padHdp * res.displayMetrics.density).toInt()
    val padV = (padVdp * res.displayMetrics.density).toInt()
    updatePaddingRelative(start = padH, top = padV, end = padH, bottom = padV)

    // reset autosize e single-line
    TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
    maxLines = 1
    isSingleLine = true
    ellipsize = TextUtils.TruncateAt.END

    // dica p/ grow: começa no máximo
    setTextSize(TypedValue.COMPLEX_UNIT_SP, maxSp.toFloat())
    if (maxSp > minSp) {
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, minSp, maxSp, max(1, config.stepSp), TypedValue.COMPLEX_UNIT_SP
        )
    }

    // ───────────────────────────────────────────────────────────────────────
    // AGRUPAMENTO AUTOMÁTICO (sem mexer no Fragment)
    // ───────────────────────────────────────────────────────────────────────
    val parentLL = parent as? LinearLayout

    // orientação atual
    val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // basePadVdp com boost em landscape
    val boostedBasePadVdp = if (isLandscape)
        (tuning.basePadVdp * tuning.landscapePadVBoostFactor).roundToInt()
    else
        tuning.basePadVdp

    if (parentLL != null && parentLL.orientation == LinearLayout.HORIZONTAL) {
        // coleta irmãos visíveis lado a lado (weight>0 e width=0)
        val group = (0 until parentLL.childCount)
            .map { parentLL.getChildAt(it) }
            .filterIsInstance<TextView>()
            .filter { v ->
                v.isVisible &&
                        (v.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                            lp.weight > 0f && lp.width == 0
                        } == true
            }

        if (group.size >= 2) {
            // líder = o mais à esquerda
            val leader = group.minByOrNull { parentLL.indexOfChild(it) }
            if (this === leader) {
                parentLL.doOnPreDraw {
                    val minPx = group.minOf { it.textSize }
                    group.forEach { tv ->
                        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                            tv, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
                        )
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, minPx)

                        // padding vertical dinâmico (com boost em landscape)
                        tv.adjustVerticalPaddingFromTextSize(
                            basePadVdp = boostedBasePadVdp,
                            baseSp = tuning.baseSp,
                            perSpDp = tuning.perSpDp,
                            minVdp = tuning.minVdp,
                            maxVdp = tuning.maxVdp
                        )
                        // ⬇️ NOVO: garante que o texto caiba sem cortar
                        tv.adjustHorizontalPaddingToFitText(minPadHdp = config.minPaddingHdp)
                    }
                }
            }
        } else {
            // botão solo no LinearLayout
            parentLL.doOnPreDraw {
                adjustVerticalPaddingFromTextSize(
                    basePadVdp = boostedBasePadVdp,
                    baseSp = tuning.baseSp,
                    perSpDp = tuning.perSpDp,
                    minVdp = tuning.minVdp,
                    maxVdp = tuning.maxVdp
                )
                adjustHorizontalPaddingToFitText(minPadHdp = config.minPaddingHdp)
            }
        }
    } else {
        // outros pais ou sem par
        this.doOnPreDraw {
            adjustVerticalPaddingFromTextSize(
                basePadVdp = boostedBasePadVdp,
                baseSp = tuning.baseSp,
                perSpDp = tuning.perSpDp,
                minVdp = tuning.minVdp,
                maxVdp = tuning.maxVdp
            )
            adjustHorizontalPaddingToFitText(minPadHdp = config.minPaddingHdp)
        }
    }
}

// ──────────────────────── Preset específico: “Nova Obra” SOLO ─────────────────────────
//
// Padding vertical mais generoso e texto maior, respeitando telas pequenas.
// (Mantém assinatura/uso que você já tinha.)
//
fun TextView.applySoloNewWorkStyle() {
    val res = resources
    val sw = res.configuration.screenWidthDp.coerceAtLeast(1)
    val tier = currentTier(sw)

    // Padding "pílula": maior vertical
    val (padHdp, padVdp, targetSp) = when (tier) {
        ScreenTier.SMALL -> Triple(22, 16, 16)  // ≤361dp
        ScreenTier.MEDIUM -> Triple(24, 18, 18)  // 362–600dp
        ScreenTier.LARGE -> Triple(30, 22, 22)  // >600dp
    }

    val padH = (padHdp * res.displayMetrics.density).toInt()
    val padV = (padVdp * res.displayMetrics.density).toInt()
    updatePaddingRelative(start = padH, top = padV, end = padH, bottom = padV)

    // Garante que nenhum autosize do XML interfira
    TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)

    // Define tamanho alvo por faixa
    setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp.toFloat())
    maxLines = 1
    isSingleLine = true
    ellipsize = TextUtils.TruncateAt.END

    // Permite encolher até 14sp se o texto não couber (e só se max>min)
    applyShrinkAutoSize(minSp = 14, maxSp = targetSp, stepSp = 1)
    this.doOnPreDraw {
        adjustHorizontalPaddingToFitText(minPadHdp = 14)
    }
}

// ───────────────── Sincronização de texto entre botões lado a lado ────────────────
//
// Mantido sem alterações de API: encontra o menor tamanho final e aplica nos dois.
//
fun View.syncTextSizesGroup(vararg views: TextView) {
    if (views.isEmpty()) return

    // Garantimos uma linha e deixamos cada um ajustar (com nosso autosize “shrink-only”)
    views.forEach { v ->
        v.maxLines = 1
        v.isSingleLine = true
    }

    // Quando todos tiverem medido, nivelamos pelo menor tamanho final
    (views.first().parent as? View ?: this).doOnPreDraw {
        val minPx = views.minOf { it.textSize }
        views.forEach { v ->
            // Desliga autosize e congela exatamente no menor px
            TextViewCompat.setAutoSizeTextTypeWithDefaults(
                v, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
            )
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, minPx)
        }
    }
}

// ───────────────────────── Helpers ─────────────────────────

private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt()

/**
 * Configura AutoSize em modo "shrink-only" com guarda para evitar a exceção:
 * se maxSp ≤ minSp, não habilita autosize (mantém tamanho fixo).
 */
private fun TextView.applyShrinkAutoSize(minSp: Int, maxSp: Int, stepSp: Int) {
    val step = max(1, stepSp)

    if (maxSp <= minSp) {
        // Evita: "Maximum auto-size text size ... is less or equal to minimum ..."
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
            this,
            TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
        )
        return
    }

    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
        this,
        /* min */ minSp,
        /* max */ maxSp,
        /* step */ step,
        TypedValue.COMPLEX_UNIT_SP
    )
}

// ---- Helpers usadas dentro do grow-shrink
private fun TextView.pxToSp(px: Float): Float {
    val dm = resources.displayMetrics
    val fontScale = resources.configuration.fontScale
    return px / (dm.density * fontScale)
}

/** Aumenta/reduz o padding vertical com base no textSize final (em sp). */
private fun TextView.adjustVerticalPaddingFromTextSize(
    basePadVdp: Int,
    baseSp: Int,
    perSpDp: Float = 0.6f,
    minVdp: Int? = null,
    maxVdp: Int? = null
) {
    val dm = resources.displayMetrics
    val finalSp = pxToSp(textSize)
    val deltaSp = (finalSp - baseSp).coerceAtLeast(0f)
    var newPadVdp = basePadVdp + (deltaSp * perSpDp).roundToInt()

    if (minVdp != null) newPadVdp = max(minVdp, newPadVdp)
    if (maxVdp != null) newPadVdp = minOf(maxVdp, newPadVdp)

    val padVpx = (newPadVdp * dm.density).roundToInt()
    updatePaddingRelative(
        start = paddingStart,
        top = padVpx,
        end = paddingEnd,
        bottom = padVpx
    )
}

// Ajusta o padding horizontal para que T0DO o texto caiba numa linha
private fun TextView.adjustHorizontalPaddingToFitText(minPadHdp: Int) {
    val dm = resources.displayMetrics
    val minPadPx = (minPadHdp * dm.density).roundToInt()

    // Garantir que não iremos elipsar
    ellipsize = null   // nunca cortar
    // continua single-line; a ideia é abrir espaço via padding
    isSingleLine = true
    maxLines = 1

    // Já estamos em doOnPreDraw quando este helper é chamado
    val textStr = text?.toString().orEmpty()
    if (textStr.isEmpty()) return

    val textW =
        paint.measureText(textStr)                   // largura real do texto na font-size final
    val avail = (measuredWidth - paddingStart - paddingEnd).toFloat()

    if (textW > avail) {
        val deficit = (textW - avail).coerceAtLeast(0f)
        // reduzir metade em cada lado, respeitando piso
        val reduceEach = (deficit / 2f).roundToInt()
        var newStart = (paddingStart - reduceEach).coerceAtLeast(minPadPx)
        var newEnd = (paddingEnd - reduceEach).coerceAtLeast(minPadPx)

        // se ainda não coube, tenta zerar toda a folga restante de um lado e depois do outro
        updatePaddingRelative(
            start = newStart,
            top = paddingTop,
            end = newEnd,
            bottom = paddingBottom
        )

        // revalida; se ainda faltar, tenta mais um ajuste simétrico até o piso
        val avail2 = (measuredWidth - newStart - newEnd).toFloat()
        if (textW > avail2 && (newStart > minPadPx || newEnd > minPadPx)) {
            newStart = minPadPx
            newEnd = minPadPx
            updatePaddingRelative(
                start = newStart,
                top = paddingTop,
                end = newEnd,
                bottom = paddingBottom
            )
        }
        // Se MESMO ASSIM não couber, o autosize (já aplicado) encolhe a fonte.
    }
}

/**
 * Botão de largura cheia (match_parent), com "grow-shrink" de texto
 * e ajuste APENAS do padding vertical proporcional ao textSize final.
 * Não toca no padding horizontal e não faz ajuste para caber.
 */
fun TextView.applyFullWidthButtonSizingGrowShrink(
    config: ButtonSizingConfig = ButtonSizingConfig(),
    tuning: GroupTuning = GroupTuning()
) {
    val res = resources
    val sw = res.configuration.screenWidthDp.coerceAtLeast(1)
    val fs = res.configuration.fontScale.coerceIn(0.85f, 1.6f)

    val tier = currentTier(sw)
    val tierMaxSpBase = when (tier) {
        ScreenTier.SMALL -> 16
        ScreenTier.MEDIUM -> {
            val t = ((sw - 362f) / (SW_MAX_DP - 362f)).coerceIn(0f, 1f)
            lerp(16, config.maxTextSp, t)
        }
        ScreenTier.LARGE -> config.maxTextSp
    }

    val minSp = config.minTextSp
    val maxSp = (tierMaxSpBase + when {
        fs >= 1.4f -> +2
        fs >= 1.2f -> +1
        else -> 0
    }).coerceAtLeast(minSp).coerceAtMost(config.maxTextSp)

    // Mantém padding H atual do style; só define um V base por tier
    val padVdp = when (tier) {
        ScreenTier.SMALL -> config.minPaddingVdp
        ScreenTier.MEDIUM -> lerp(config.minPaddingVdp, config.maxPaddingVdp, 0.5f)
        ScreenTier.LARGE -> config.maxPaddingVdp
    }
    val padVpx = (padVdp * res.displayMetrics.density).roundToInt()
    updatePaddingRelative(start = paddingStart, top = padVpx, end = paddingEnd, bottom = padVpx)

    // Configura autosize grow-shrink
    TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
    maxLines = 1
    isSingleLine = true
    ellipsize = TextUtils.TruncateAt.END
    setTextSize(TypedValue.COMPLEX_UNIT_SP, maxSp.toFloat())
    if (maxSp > minSp) {
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, minSp, maxSp, max(1, config.stepSp), TypedValue.COMPLEX_UNIT_SP
        )
    }

    // Ajuste V dinâmico em função do textSize final (apenas vertical)
    this.doOnPreDraw {
        val isLandscape = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val boostedBasePadVdp = if (isLandscape)
            (tuning.basePadVdp * tuning.landscapePadVBoostFactor).roundToInt()
        else tuning.basePadVdp

        adjustVerticalPaddingFromTextSize(
            basePadVdp = boostedBasePadVdp,
            baseSp = tuning.baseSp,
            perSpDp = tuning.perSpDp,
            minVdp = tuning.minVdp,
            maxVdp = tuning.maxVdp
        )
        // ⚠️ Sem ajuste horizontal para caber.
    }
}