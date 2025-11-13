package com.luizeduardobrandao.obra.ui.calculo.ui

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Helper responsável por calcular dimensões responsivas para a tabela de materiais
 * Considera: tamanho de tela, orientação, densidade e escala de fonte (acessibilidade)
 */
class TableResponsiveHelper(private val context: Context) {

    private val cfg = context.resources.configuration

    // Largura base usada pelo sistema para escolher values-swXXXdp
    private val smallestWidthDp: Int =
        if (cfg.smallestScreenWidthDp > 0) cfg.smallestScreenWidthDp else cfg.screenWidthDp

    // Largura atual da janela (portrait/landscape, multi-janela, etc.)
    // private val screenWidthDp: Int = cfg.screenWidthDp

    // private val fontScale: Float = cfg.fontScale
    private val isLandscape: Boolean = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE

    private enum class ScreenSize {
        PHONE_COMPACT,      // Telas pequenas (ou phones "apertados")
        PHONE_NORMAL,       // Smartphones típicos (ex: J8, intermediários)
        TABLET,             // Tablets pequenos/médios
        TABLET_LARGE        // Tablets grandes
    }

    /**
     * Classificação baseada em smallestWidthDp (igual ao usado por values-swXXXdp):
     *
     * - PHONE_COMPACT : sw < 361dp   → ex: S24 "apertado", devices menores
     * - PHONE_NORMAL  : sw < 600dp   → ex: Galaxy J8, phones mais largos
     * - TABLET        : sw < 840dp   → tablets pequenos / fold
     * - TABLET_LARGE  : sw ≥ 840dp   → tablets grandes / modo desktop
     */
    private val screenSize: ScreenSize = when {
        smallestWidthDp < 361 -> ScreenSize.PHONE_COMPACT
        smallestWidthDp < 600 -> ScreenSize.PHONE_NORMAL
        smallestWidthDp < 840 -> ScreenSize.TABLET
        else -> ScreenSize.TABLET_LARGE
    }

    // ==================== CARD DA TABELA ====================

    val cardCornerRadius: Float
        get() = when (screenSize) {
            ScreenSize.PHONE_COMPACT -> if (isLandscape) dpToPxF(7f) else dpToPxF(9f)
            ScreenSize.PHONE_NORMAL -> if (isLandscape) dpToPxF(8f) else dpToPxF(10f)
            ScreenSize.TABLET -> if (isLandscape) dpToPxF(10f) else dpToPxF(12f)
            ScreenSize.TABLET_LARGE -> dpToPxF(14f)
        }

    val cardElevation: Float
        get() = when (screenSize) {
            ScreenSize.PHONE_COMPACT -> dpToPxF(2f)
            ScreenSize.PHONE_NORMAL -> dpToPxF(2.5f)
            ScreenSize.TABLET -> dpToPxF(3f)
            ScreenSize.TABLET_LARGE -> dpToPxF(3.5f)
        }

    // ==================== CABEÇALHO ====================

    val headerMinHeight: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 20 else 22
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 22 else 24
                ScreenSize.TABLET -> if (isLandscape) 24 else 26
                ScreenSize.TABLET_LARGE -> 30
            }
        )

    val headerPaddingVertical: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 10 else 12
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 12 else 14
                ScreenSize.TABLET -> if (isLandscape) 14 else 16
                ScreenSize.TABLET_LARGE -> 18
            }
        )

    val headerPaddingHorizontal: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 12 else 14
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 16 else 18
                ScreenSize.TABLET -> if (isLandscape) 18 else 20
                ScreenSize.TABLET_LARGE -> 22
            }
        )

    val headerTextSize: Float
        get() {
            val baseSize = when (screenSize) {
                ScreenSize.PHONE_COMPACT -> 15.5f
                ScreenSize.PHONE_NORMAL -> 16f
                ScreenSize.TABLET -> 17.5f
                ScreenSize.TABLET_LARGE -> 18f
            }
            // Landscape: texto ligeiramente menor pra caber melhor
            return if (isLandscape) baseSize - 0.5f else baseSize
        }

    val headerLetterSpacing: Float
        get() = when (screenSize) {
            ScreenSize.PHONE_COMPACT -> 0.04f
            ScreenSize.PHONE_NORMAL -> 0.045f
            ScreenSize.TABLET -> 0.05f
            ScreenSize.TABLET_LARGE -> 0.06f
        }

    // ==================== LINHAS DE DADOS ====================

    val rowMinHeight: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 46 else 50
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 48 else 52
                ScreenSize.TABLET -> if (isLandscape) 56 else 60
                ScreenSize.TABLET_LARGE -> 66
            }
        )

    val rowPaddingVertical: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 8 else 10
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 10 else 12
                ScreenSize.TABLET -> if (isLandscape) 14 else 16
                ScreenSize.TABLET_LARGE -> 22
            }
        )

    val rowPaddingHorizontal: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 4 else 6
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 6 else 8
                ScreenSize.TABLET -> if (isLandscape) 8 else 10
                ScreenSize.TABLET_LARGE -> 14
            }
        )

    val rowTextSize: Float
        get() {
            val baseSize = when (screenSize) {
                ScreenSize.PHONE_COMPACT -> 14f
                ScreenSize.PHONE_NORMAL -> 15f
                ScreenSize.TABLET -> 16f
                ScreenSize.TABLET_LARGE -> 17f
            }
            return if (isLandscape) baseSize - 0.5f else baseSize
        }

    val rowComprarTextSize: Float
        get() {
            val baseSize = when (screenSize) {
                ScreenSize.PHONE_COMPACT -> 14f
                ScreenSize.PHONE_NORMAL -> 15f
                ScreenSize.TABLET -> 16f
                ScreenSize.TABLET_LARGE -> 17f
            }
            // Mantém leve destaque mas acompanha rowTextSize
            return if (isLandscape) baseSize - 0.5f else baseSize
        }

    val observacaoTextSize: Float
        get() {
            val baseSize = when (screenSize) {
                ScreenSize.PHONE_COMPACT -> 12f
                ScreenSize.PHONE_NORMAL -> 13f
                ScreenSize.TABLET -> 13.5f
                ScreenSize.TABLET_LARGE -> 14f
            }
            return if (isLandscape) baseSize - 0.5f else baseSize
        }

    val observacaoMarginTop: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 6 else 7
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 7 else 8
                ScreenSize.TABLET -> if (isLandscape) 9 else 10
                ScreenSize.TABLET_LARGE -> 12
            }
        )

    // Controla Margin do primeiro e último elemento da Lista
    private val firstRowMarginTop: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 3 else 5
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 4 else 6
                ScreenSize.TABLET -> if (isLandscape) 6 else 8
                ScreenSize.TABLET_LARGE -> 10
            }
        )

    private val lastRowMarginBottom: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 5 else 7
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 6 else 8
                ScreenSize.TABLET -> if (isLandscape) 8 else 10
                ScreenSize.TABLET_LARGE -> 12
            }
        )

    /**
     * Aplica marginTop apenas no primeiro item de dados
     * e marginBottom apenas no último item de dados.
     *
     * Se skipHeader = true, o índice 0 (cabeçalho) é ignorado
     * e o primeiro item considerado é o índice 1.
     *
     * Não altera margens dos itens intermediários.
     */
    fun applyEdgeItemMargins(container: ViewGroup, skipHeader: Boolean = false) {
        val childCount = container.childCount
        val firstIndex = if (skipHeader) 1 else 0

        // Se não há itens suficientes depois do cabeçalho, não faz nada
        if (childCount <= firstIndex) return

        val lastIndex = childCount - 1

        for (i in firstIndex..lastIndex) {
            val child = container.getChildAt(i)
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams ?: continue

            if (i == firstIndex) {
                // Primeiro item de dados (não o cabeçalho)
                lp.topMargin = firstRowMarginTop
            }

            if (i == lastIndex) {
                // Último item de dados
                lp.bottomMargin = lastRowMarginBottom
            }

            child.layoutParams = lp
        }
    }

    // ==================== TÍTULO "Lista de Materiais" ====================

    val titleTextSize: Float
        get() {
            val baseSize = when (screenSize) {
                ScreenSize.PHONE_COMPACT -> 20f
                ScreenSize.PHONE_NORMAL -> 21f
                ScreenSize.TABLET -> 23f
                ScreenSize.TABLET_LARGE -> 25f
            }
            return if (isLandscape) baseSize - 1f else baseSize
        }

    val titleMarginTop: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 2 else 4
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 2 else 4
                ScreenSize.TABLET -> if (isLandscape) 6 else 8
                ScreenSize.TABLET_LARGE -> 12
            }
        )

    val titleMarginBottom: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 10 else 14
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 12 else 16
                ScreenSize.TABLET -> if (isLandscape) 14 else 18
                ScreenSize.TABLET_LARGE -> 20
            }
        )

    // ==================== CARD DE INFORMAÇÃO ====================

    val infoCardMarginTop: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 10 else 14
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 12 else 16
                ScreenSize.TABLET -> if (isLandscape) 14 else 18
                ScreenSize.TABLET_LARGE -> 20
            }
        )

    val infoCardCornerRadius: Float
        get() = when (screenSize) {
            ScreenSize.PHONE_COMPACT -> if (isLandscape) dpToPxF(10f) else dpToPxF(12f)
            ScreenSize.PHONE_NORMAL -> if (isLandscape) dpToPxF(12f) else dpToPxF(14f)
            ScreenSize.TABLET -> if (isLandscape) dpToPxF(14f) else dpToPxF(16f)
            ScreenSize.TABLET_LARGE -> dpToPxF(18f)
        }

    val infoCardPadding: Int
        get() = dpToPx(
            when (screenSize) {
                ScreenSize.PHONE_COMPACT -> if (isLandscape) 12 else 14
                ScreenSize.PHONE_NORMAL -> if (isLandscape) 14 else 16
                ScreenSize.TABLET -> if (isLandscape) 16 else 18
                ScreenSize.TABLET_LARGE -> 20
            }
        )

    val infoCardTextSize: Float
        get() {
            val baseSize = when (screenSize) {
                ScreenSize.PHONE_COMPACT -> 13f
                ScreenSize.PHONE_NORMAL -> 14f
                ScreenSize.TABLET -> 14.5f
                ScreenSize.TABLET_LARGE -> 15f
            }
            return if (isLandscape) baseSize - 0.5f else baseSize
        }

    // ==================== GUIDELINES (COLUNAS) ====================

    val guidelineItemEnd: Float
        get() = when (screenSize) {
            ScreenSize.PHONE_COMPACT -> if (isLandscape) 0.38f else 0.40f
            ScreenSize.PHONE_NORMAL -> if (isLandscape) 0.40f else 0.42f
            ScreenSize.TABLET -> if (isLandscape) 0.41f else 0.43f
            ScreenSize.TABLET_LARGE -> 0.44f
        }

    val guidelineQtdEnd: Float
        get() = when (screenSize) {
            ScreenSize.PHONE_COMPACT -> if (isLandscape) 0.58f else 0.60f
            ScreenSize.PHONE_NORMAL -> if (isLandscape) 0.60f else 0.62f
            ScreenSize.TABLET -> if (isLandscape) 0.62f else 0.65f
            ScreenSize.TABLET_LARGE -> 0.64f
        }

    // ==================== UTILITIES ====================

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()
    }

    private fun dpToPxF(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    fun setTextSizeSp(textView: TextView, sizeSp: Float) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    // ==================== DEBUG (OPCIONAL) ====================

    /**
     * Retorna informações de debug sobre a configuração atual
     * Útil para logs durante desenvolvimento
     */
//    val density = context.resources.displayMetrics.density
//    fun getDebugInfo(): String {
//        return """
//            |TableResponsiveHelper Config:
//            |  CornerRadiusPx: $cardCornerRadius  |  CornerRadiusDp: ${cardCornerRadius / density}
//            |  CardElevation: $cardElevation   |   CardElevationDp: ${cardElevation / density}
//            |  .
//            |  CabeçalhoMinHeight: $headerMinHeight   |   CabeçalhoMinHeightDp: ${headerMinHeight / density}
//            |  CabeçalhoPaddingVertical: $headerPaddingVertical   |   CabeçalhoPaddingVerticalDp: ${headerPaddingVertical / density}
//            |  CabeçalhoPaddingHorizontal: $headerPaddingHorizontal   |   CabeçalhoPaddingHorizontalDp: ${headerPaddingHorizontal / density}
//            |  .
//            |  LinhasMinHeight: $rowMinHeight    |   LinhasMinHeightDp: ${rowMinHeight / density}
//            |  LinhasPaddingVertical: $rowPaddingVertical    |    LinhasPaddingVerticalDp: ${rowPaddingVertical / density}
//            |  LinhasPaddingHorizontal: $rowPaddingHorizontal    |    LinhasPaddingHorizontalDp: ${rowPaddingHorizontal / density}
//            |  .
//            |  ObservacaoMargin: $observacaoMarginTop    |    ObservacaoMarginDp: ${observacaoMarginTop / density}
//            |  .
//            |  ScreenSize: $screenSize
//            |  Width (current): ${screenWidthDp}dp
//            |  SmallestWidth: ${smallestWidthDp}dp
//            |  FontScale: $fontScale
//            |  Orientation: ${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}
//            |  Density: ${context.resources.displayMetrics.density}
//        """.trimMargin()
//    }
}