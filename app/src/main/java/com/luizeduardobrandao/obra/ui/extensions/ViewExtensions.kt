@file:Suppress("unused")

package com.luizeduardobrandao.obra.ui.extensions

import androidx.fragment.app.Fragment
import com.luizeduardobrandao.obra.ui.snackbar.SnackbarFragment
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.NestedScrollView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

/**
 * Abre o [SnackbarFragment] em *modal bottom-sheet* para exibir
 * erros, avisos ou mensagens de sucesso de forma padronizada.
 *
 * @param type      controla cor de fundo / ícone interno no fragment (ex.: "error", "success")
 * @param title     título centralizado
 * @param msg       texto abaixo do título
 * @param btnText   rótulo do botão de ação (opcional – deixa oculto se nulo ou vazio)
 */
fun Fragment.showSnackbarFragment(
    type: String,
    title: String,
    msg: String,
    btnText: String? = null,
    onAction: (() -> Unit)? = null,
    // NOVOS opcionais:
    btnNegativeText: String? = null,
    onNegative: (() -> Unit)? = null
) {
    val tag = SnackbarFragment.TAG
    val fm = parentFragmentManager
    if (fm.findFragmentByTag(tag) != null) return

    val sheet = SnackbarFragment.newInstance(type, title, msg, btnText, btnNegativeText)
    sheet.actionCallback = onAction
    sheet.secondaryActionCallback = onNegative // NOVO

    sheet.show(fm, tag)
}

/**
 * Esconde o teclado (caso esteja aberto) a partir de qualquer [View].
 */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

// ───────────────── FAB de rolagem: helpers ─────────────────

/** Verdadeiro quando o NestedScrollView já está no final (com tolerância). */
fun NestedScrollView.isAtBottom(thresholdPx: Int = 8): Boolean {
    val child = getChildAt(0) ?: return true
    val diff = child.bottom - (scrollY + height)
    return diff <= thresholdPx
}

/** (Opcional) Verdadeiro quando está no topo. */
fun NestedScrollView.isAtTop(thresholdPx: Int = 8): Boolean =
    !canScrollVertically(-1) || scrollY <= thresholdPx

/** Anima a visibilidade do FAB usando as animações padrão do Material (scale + fade). */
fun FloatingActionButton.updateFabVisibilityAnimated(visible: Boolean) {
    if (visible) {
        if (!isShown) show()
    } else {
        if (isShown) hide()
    }
}

/**
 * Liga o comportamento:
 *  • Mostrar FAB somente quando (é edição) && (!salvando) && (!no final).
 *  • Ao clicar: esconde e rola até o final.
 *  • Reaparece quando o usuário rolar para cima.
 *  • Com animação (show/hide do FAB).
 */
fun bindScrollToBottomFabBehavior(
    fab: FloatingActionButton,
    scrollView: NestedScrollView,
    isEditProvider: () -> Boolean,
    isSavingProvider: () -> Boolean
) {
    fun recomputeVisibility() {
        val shouldShow = isEditProvider() && !isSavingProvider() && !scrollView.isAtBottom()
        fab.updateFabVisibilityAnimated(shouldShow)
    }

    // Clique: esconde e rola até o final (inline, sem depender de outra extensão)
    fab.setOnClickListener {
        fab.updateFabVisibilityAnimated(false)
        scrollView.post {
            val bottom = scrollView.getChildAt(0)?.bottom ?: 0
            scrollView.smoothScrollTo(0, bottom)
        }
    }

    // Recalcula em cada scroll
    scrollView.setOnScrollChangeListener { _, _, _, _, _ -> recomputeVisibility() }

    // Recalcula no primeiro layout
    scrollView.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                recomputeVisibility()
                scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

    // Chamada inicial
    recomputeVisibility()
}

// ─────────── FAB de rolagem: Fragment Resumo ───────────
/** Verdadeiro quando o conteúdo do NestedScrollView é maior que a viewport (logo, há rolagem). */
fun NestedScrollView.hasScrollableContent(thresholdPx: Int = 4): Boolean {
    val child = getChildAt(0) ?: return false
    val contentH = child.height - paddingTop - paddingBottom
    return contentH - height > thresholdPx
}

/**
 * Comportamento do FAB no Resumo:
 *  • Inicialmente invisível;
 *  • Fica visível somente se o conteúdo for rolável (abas abertas) e NÃO estiver no fim;
 *  • Some ao chegar no fim e volta a aparecer quando subir;
 *  • Clique: esconde e rola suavemente até o final;
 *  • Usa animação padrão do FAB (show/hide).
 *
 * Retorna o OnGlobalLayoutListener anexado para remoção em onDestroyView.
 */
fun bindScrollToBottomFabForResumo(
    fab: FloatingActionButton,
    scrollView: NestedScrollView
): ViewTreeObserver.OnGlobalLayoutListener {
    // Estado inicial: invisível
    fab.updateFabVisibilityAnimated(false)

    fun recomputeVisibility() {
        val shouldShow = scrollView.hasScrollableContent() && !scrollView.isAtBottom()
        fab.updateFabVisibilityAnimated(shouldShow)
    }

    // Clique → esconde e rola até o fim
    fab.setOnClickListener {
        fab.updateFabVisibilityAnimated(false)
        scrollView.post {
            val bottom = scrollView.getChildAt(0)?.bottom ?: 0
            scrollView.smoothScrollTo(0, bottom)
        }
    }

    // Reavalia a cada rolagem
    scrollView.setOnScrollChangeListener { _, _, _, _, _ -> recomputeVisibility() }

    // Reavalia a cada novo layout (expansão/retração das abas dispara layouts)
    val gl = ViewTreeObserver.OnGlobalLayoutListener { recomputeVisibility() }
    scrollView.viewTreeObserver.addOnGlobalLayoutListener(gl)

    // Chamada inicial (após anexar listeners)
    scrollView.post { recomputeVisibility() }

    return gl
}

// ─────────── FAB de rolagem: RecyclerView (FotosFragment) ───────────

/** Verdadeiro quando o conteúdo do RecyclerView é maior que a viewport (há rolagem). */
fun RecyclerView.hasScrollableContent(thresholdPx: Int = 4): Boolean {
    val range = computeVerticalScrollRange()
    val extent = computeVerticalScrollExtent()
    return (range - extent) > thresholdPx
}

/** Verdadeiro quando o RecyclerView já está no final (com pequena tolerância). */
fun RecyclerView.isAtBottom(thresholdPx: Int = 8): Boolean {
    val extent = computeVerticalScrollExtent()
    val offset = computeVerticalScrollOffset()
    val range  = computeVerticalScrollRange()
    return offset + extent >= (range - thresholdPx)
}

/** Handle para remover listeners ao destruir a View. */
data class RecyclerFabBindings(
    val layoutListener: ViewTreeObserver.OnGlobalLayoutListener,
    val scrollListener: RecyclerView.OnScrollListener,
    val dataObserver: RecyclerView.AdapterDataObserver?
)

/**
 * Comportamento do FAB no Recycler:
 *  • Visível somente se houver rolagem possível e NÃO estiver no fim;
 *  • Some ao chegar no fim e reaparece quando subir;
 *  • Clique: esconde e faz smoothScroll até o último item;
 *  • Usa animação padrão do FAB (show/hide).
 *
 * Retorna um [RecyclerFabBindings] para remoção em onDestroyView().
 */
fun bindScrollToBottomFabForRecycler(
    fab: FloatingActionButton,
    recyclerView: RecyclerView
): RecyclerFabBindings {
    // estado inicial
    fab.updateFabVisibilityAnimated(false)

    fun recomputeVisibility() {
        val shouldShow = recyclerView.hasScrollableContent() && !recyclerView.isAtBottom()
        fab.updateFabVisibilityAnimated(shouldShow)
    }

    // Clique → esconde e rola até o último item
    fab.setOnClickListener {
        fab.updateFabVisibilityAnimated(false)
        recyclerView.adapter?.let { adapter ->
            val last = (adapter.itemCount - 1).coerceAtLeast(0)
            recyclerView.smoothScrollToPosition(last)
        }
    }

    // Listener de scroll
    val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            recomputeVisibility()
        }
    }
    recyclerView.addOnScrollListener(scrollListener)

    // Reavaliar após cada layout (mudanças de tamanho/itens)
    val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { recomputeVisibility() }
    recyclerView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

    // Reavaliar quando a lista muda (garante retorno Unit)
    val observer = recyclerView.adapter?.let { ad ->
        object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                recyclerView.post { recomputeVisibility() }
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                recyclerView.post { recomputeVisibility() }
            }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                recyclerView.post { recomputeVisibility() }
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                recyclerView.post { recomputeVisibility() }
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                recyclerView.post { recomputeVisibility() }
            }
        }.also { ad.registerAdapterDataObserver(it) }
    }

    // Chamada inicial
    recyclerView.post { recomputeVisibility() }

    return RecyclerFabBindings(layoutListener, scrollListener, observer)
}
