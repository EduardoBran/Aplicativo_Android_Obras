package com.luizeduardobrandao.obra.ui.cronograma.gantt.anim

import android.annotation.SuppressLint
import android.view.animation.Interpolator
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.luizeduardobrandao.obra.R
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

/** Animações Cabeçalho */
object GanttHeaderAnimator {

    /** Estado por container, sem usar setTag com chave inteira. */
    private data class HeaderState(
        var animatedOnce: Boolean = false,
        var introDone: Boolean = false,
        // Campos para o fader
        var fadeListener: ViewTreeObserver.OnScrollChangedListener? = null,
        var fadeVto: ViewTreeObserver? = null,
        // Lembrar o HSV para recalcular quando a intro acabar
        var headerScrollRef: java.lang.ref.WeakReference<HorizontalScrollView>? = null
    )

    private val states = WeakHashMap<ViewGroup, HeaderState>()

    private fun stateOf(container: ViewGroup): HeaderState =
        states.getOrPut(container) { HeaderState() }

    /**
     * Anima a ENTRADA visual dos itens do header (datas).
     * Não mexe em layout; usa alpha/translationX. Marca o estado internamente.
     */
    @JvmStatic
    fun animateInDates(
        container: ViewGroup,
        durationMs: Long = 420L,
        staggerMs: Long = 24L,
        offsetPx: Float = 20f, // passe em px
        interpolator: Interpolator = FastOutSlowInInterpolator()
    ) {
        container.doOnPreDraw {
            val count = container.childCount
            if (count == 0) return@doOnPreDraw

            val st = stateOf(container)
            // já animou? sai
            if (st.animatedOnce) return@doOnPreDraw
            st.animatedOnce = true // equivalente sem usar setTag(key,...)

            // estado inicial
            for (i in 0 until count) {
                val v: View = container.getChildAt(i)
                v.alpha = 0f
                v.translationX = offsetPx
            }

            // anima em cascata
            for (i in 0 until count) {
                val anim = container.getChildAt(i)
                    .animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(durationMs)
                    .setStartDelay(i * staggerMs)
                    .setInterpolator(interpolator)
                    .withLayer()

                if (i == count - 1) {
                    anim.withEndAction {
                        st.introDone = true
                        // recalc imediato com o X atual (mesmo que não haja novo scroll)
                        st.headerScrollRef?.get()?.let { hsv ->
                            container.post { updateAlphaFromScroll(hsv, container, st) }
                        }
                    }
                }
                anim.start()
            }
        }
    }

    /** Atualiza alpha de cada item de data conforme a fração visível no viewport do header. */
    private fun updateAlphaFromScroll(
        headerScroll: HorizontalScrollView,
        container: ViewGroup,
        st: HeaderState
    ) {
        // Não interfere durante a animação de entrada
        if (!st.introDone) return

        // Se ainda não houver medidas válidas, não calcule alpha
        if (headerScroll.width <= 0 || container.width <= 0) return

        // --- DELTA entre o início “visual” das timelines e o início do header ---
        val firstGap =
            container.resources.getDimensionPixelSize(R.dimen.gantt_first_cell_margin_start)
        val leftDelta = (container.paddingLeft - firstGap).coerceAtLeast(0)

        // Janela virtual alinhada ao viewport das timelines
        val leftBound = headerScroll.scrollX + leftDelta
        val rightBound = leftBound + headerScroll.width

        val count = container.childCount
        for (i in 0 until count) {
            val child = container.getChildAt(i) ?: continue
            val childLeft = child.left
            val childRight = childLeft + child.width
            val visiblePx =
                (min(childRight, rightBound) - max(childLeft, leftBound)).coerceAtLeast(0)
            val frac = if (child.width > 0) visiblePx.toFloat() / child.width.toFloat() else 0f
            child.alpha = frac.coerceIn(0f, 1f)
        }
    }


    /**
     * Ativa o "fade por visibilidade" das datas do header enquanto há scroll.
     * - Não roda junto com a animação inicial (respeita introDone).
     * - Não mexe em listeners existentes do header (usa ViewTreeObserver, que é aditivo).
     */
    @JvmStatic
    fun enableScrollFade(
        headerScroll: HorizontalScrollView,
        container: ViewGroup
    ) {
        val st = stateOf(container)
        st.headerScrollRef = java.lang.ref.WeakReference(headerScroll)

        // Se já estiver ativo, apenas força 1 atualização com o scroll atual
        st.fadeListener?.let {
            updateAlphaFromScroll(headerScroll, container, st)
            return
        }

        // Listener aditivo (não substitui o setOnScrollChangeListener do adapter)
        val vto = headerScroll.viewTreeObserver
        val listener = ViewTreeObserver.OnScrollChangedListener {
            updateAlphaFromScroll(headerScroll, container, st)
        }
        vto.addOnScrollChangedListener(listener)
        st.fadeListener = listener
        st.fadeVto = vto

        // Atualização inicial: só aplica após a intro.
        // Se a intro ainda não terminou, programa uma checagem única depois.
        container.post {
            if (st.introDone) {
                updateAlphaFromScroll(headerScroll, container, st)
            } else {
                val vto1 = container.viewTreeObserver
                val pre = object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (!container.isAttachedToWindow) return true
                        if (st.introDone) {
                            if (vto1.isAlive) vto1.removeOnPreDrawListener(this)
                            // intro acabou → recalc imediato
                            st.headerScrollRef?.get()?.let { hsv ->
                                updateAlphaFromScroll(hsv, container, st)
                            }
                        }
                        return true
                    }
                }
                if (vto1.isAlive) vto1.addOnPreDrawListener(pre)
            }
        }
    }

    @JvmStatic
    fun requestFadeRecalc(headerScroll: HorizontalScrollView, container: ViewGroup) {
        val st = stateOf(container)
        // Se a intro ainda não foi liberada, não há o que recalcular
        if (!st.introDone) return

        // Garante que o cálculo rode só depois de o HSV estar medido/posicionado
        headerScroll.viewTreeObserver?.let {
            if (headerScroll.width > 0 && container.width > 0) {
                updateAlphaFromScroll(headerScroll, container, st)
            } else {
                headerScroll.post {
                    headerScroll.viewTreeObserver?.addOnPreDrawListener(
                        object : ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                if (headerScroll.width > 0 && container.width > 0) {
                                    if (headerScroll.viewTreeObserver.isAlive) {
                                        headerScroll.viewTreeObserver.removeOnPreDrawListener(this)
                                    }
                                    updateAlphaFromScroll(headerScroll, container, st)
                                }
                                return true
                            }
                        }
                    )
                }
            }
        }
    }

    @JvmStatic
    fun markIntroDone(container: ViewGroup) {
        val st = stateOf(container)
        st.introDone = true
        st.headerScrollRef?.get()?.let { hsv ->
            container.post { updateAlphaFromScroll(hsv, container, st) }
        }
    }

    /** Desativa o fade e remove listeners para evitar leaks. */
    @JvmStatic
    fun disableScrollFade(container: ViewGroup) {
        val st = states[container] ?: return
        val vto = st.fadeVto
        val l = st.fadeListener
        if (vto != null && vto.isAlive && l != null) {
            vto.removeOnScrollChangedListener(l)
        }
        st.fadeListener = null
        st.fadeVto = null
        // Opcional: restaurar visibilidade total ao desligar
        for (i in 0 until container.childCount) {
            container.getChildAt(i)?.alpha = 1f
        }
    }

    @JvmStatic
    private fun finishIntroNow(headerScroll: HorizontalScrollView, container: ViewGroup) {
        val st = stateOf(container)
        if (st.introDone) return
        for (i in 0 until container.childCount) {
            container.getChildAt(i)?.animate()?.cancel()
            container.getChildAt(i)?.apply {
                alpha = 1f
                translationX = 0f
            }
        }
        st.introDone = true
        // aplica o fade no X atual imediatamente
        updateAlphaFromScroll(headerScroll, container, st)
    }

    /**
     * Finaliza a intro SOMENTE quando houver gesto real de arrasto horizontal do usuário
     * (dx > touchSlop e dx > dy). Ignora scrolls programáticos.
     *
     * Não consome o evento (retorna false).
     */
    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun installEarlyFinishGestures(
        headerScroll: HorizontalScrollView,
        container: ViewGroup,
        recycler: RecyclerView
    ) {
        val touchSlop = ViewConfiguration.get(headerScroll.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var finished = false

        fun onTouch(ev: MotionEvent, v: View): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!finished) {
                        val dx = kotlin.math.abs(ev.x - downX)
                        val dy = kotlin.math.abs(ev.y - downY)
                        // só considera gesto horizontal acima do slop
                        if (dx > touchSlop && dx > dy) {
                            finishIntroNow(headerScroll, container)
                            finished = true
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // acessibilidade (lint)
                    v.performClick()
                }
            }
            return false // não consome; deixa o scroll funcionar
        }

        val tlHeader = View.OnTouchListener { v, ev -> onTouch(ev, v) }
        val tlRecycler = View.OnTouchListener { v, ev -> onTouch(ev, v) }

        // Observa o gesto no header e nas linhas (RecyclerView)
        headerScroll.setOnTouchListener(tlHeader)
        recycler.setOnTouchListener(tlRecycler)

        // Guarda referência do HSV para o recalc no fim da intro (Alteração 1 já faz isso)
        stateOf(container).headerScrollRef = java.lang.ref.WeakReference(headerScroll)
    }

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun installEarlyFinishOnRowScroll(
        rowScroll: HorizontalScrollView,
        headerScroll: HorizontalScrollView,
        container: ViewGroup
    ) {
        val touchSlop = ViewConfiguration.get(rowScroll.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var finished = false

        val tl = View.OnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
                MotionEvent.ACTION_MOVE -> {
                    if (!finished) {
                        val dx = kotlin.math.abs(ev.x - downX)
                        val dy = kotlin.math.abs(ev.y - downY)
                        if (dx > touchSlop && dx > dy) {
                            finishIntroNow(headerScroll, container)
                            finished = true
                        }
                    }
                }
                MotionEvent.ACTION_UP -> v.performClick()
            }
            false
        }
        rowScroll.setOnTouchListener(tl)
    }

    @SuppressLint("ClickableViewAccessibility")
    @JvmStatic
    fun uninstallEarlyFinishOnRowScroll(rowScroll: HorizontalScrollView) {
        rowScroll.setOnTouchListener(null)
    }

    /** Opcional: utilitário para limpar (por exemplo, em onDestroyView) */
    @SuppressLint("ClickableViewAccessibility")
    @JvmStatic
    fun uninstallEarlyFinishGestures(headerScroll: HorizontalScrollView, recycler: RecyclerView) {
        headerScroll.setOnTouchListener(null)
        recycler.setOnTouchListener(null)
    }
}