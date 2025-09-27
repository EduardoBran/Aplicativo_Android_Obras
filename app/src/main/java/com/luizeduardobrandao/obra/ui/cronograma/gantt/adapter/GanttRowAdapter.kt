package com.luizeduardobrandao.obra.ui.cronograma.gantt.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.databinding.ItemGanttRowBinding
import com.luizeduardobrandao.obra.utils.GanttUtils
import java.lang.ref.WeakReference
import java.time.LocalDate

class GanttRowAdapter(
    private val onToggleDay: (Etapa, Set<String>) -> Unit,
    private val requestHeaderDays: () -> List<LocalDate>
) : ListAdapter<Etapa, GanttRowAdapter.VH>(DIFF) {

    init {
        setHasStableIds(true)
    }

    // sincronização básica do scroll: cabeçalho <-> linhas
    private var headerScrollRef: WeakReference<HorizontalScrollView>? = null
    private val rowScrolls = mutableListOf<WeakReference<HorizontalScrollView>>()
    private var syncing = false

    private fun propagateFromRow(source: HorizontalScrollView, scrollX: Int) {
        if (syncing) return
        syncing = true
        // Cabeçalho anda junto
        headerScrollRef?.get()?.scrollTo(scrollX, 0)

        // Todas as outras linhas andam junto
        val it = rowScrolls.iterator()
        while (it.hasNext()) {
            val row = it.next().get()
            if (row == null) {
                it.remove() // limpa referências mortas
                continue
            }
            if (row !== source) row.scrollTo(scrollX, 0)
        }
        syncing = false
    }

    var onFirstLeftWidth: ((Int) -> Unit)? = null

    fun attachHeaderScroll(header: HorizontalScrollView) {
        headerScrollRef = WeakReference(header)
        header.setOnScrollChangeListener { _: View, scrollX: Int, _: Int, _: Int, _: Int ->
            if (syncing) return@setOnScrollChangeListener
            syncing = true
            rowScrolls.forEach { ref -> ref.get()?.scrollTo(scrollX, 0) }
            syncing = false
        }
        // também propaga scroll das linhas para o cabeçalho
        rowScrolls.forEach { ref ->
            ref.get()?.setOnScrollChangeListener { v: View, sx: Int, _: Int, _: Int, _: Int ->
                propagateFromRow(v as HorizontalScrollView, sx)
            }
        }
    }

    fun detachHeaderScroll() {
        headerScrollRef = null
        rowScrolls.clear()
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    inner class VH(private val b: ItemGanttRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(etapa: Etapa) = with(b) {
            // ——— Coluna fixa (texto) ———
            tvTitulo.text =
                etapa.titulo.ifBlank { itemView.context.getString(R.string.gantt_col_tarefa) }
            tvPeriodo.text = itemView.context.getString(
                R.string.cronograma_date_range,
                etapa.dataInicio,
                etapa.dataFim
            )
            tvProgresso.text =
                itemView.context.getString(R.string.gantt_progress_fmt, etapa.progresso)

            // ——— Timeline: cabeçalho global + range da etapa ———
            val headerDays = requestHeaderDays()
            val etapaStart = GanttUtils.brToLocalDateOrNull(etapa.dataInicio)
            val etapaEnd = GanttUtils.brToLocalDateOrNull(etapa.dataFim)

            timelineView.apply {
                setHeaderDays(headerDays)
                setEtapaRange(etapaStart, etapaEnd)
                setDiasConcluidosUtc(etapa.diasConcluidos?.toSet() ?: emptySet())
                setOnDayToggleListener { newSetUtc ->
                    // 1) feedback instantâneo na linha
                    val pct =
                        GanttUtils.calcularProgresso(newSetUtc, etapa.dataInicio, etapa.dataFim)
                    tvProgresso.text = itemView.context.getString(R.string.gantt_progress_fmt, pct)
                    // 2) persiste (VM recalcula e manda pro DB)
                    onToggleDay(etapa, newSetUtc)
                }
                // garante que a largura acompanhe o número de dias
                requestLayout()
                invalidate()
            }

            rowScroll.isHorizontalScrollBarEnabled = true

            // ——— Sincronização com o cabeçalho ———
            rowScrolls.add(WeakReference(rowScroll))
            headerScrollRef?.get()?.let {
                rowScroll.setOnScrollChangeListener { v: View, scrollX: Int, _: Int, _: Int, _: Int ->
                    propagateFromRow(v as HorizontalScrollView, scrollX)
                }
            }

            // ——— Reporta a largura da coluna fixa (apenas no 1º item) para alinhar o header ———
            val pos = absoluteAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos == 0) {
                leftColumn.post { onFirstLeftWidth?.invoke(leftColumn.width) }
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val row = holder.itemView.findViewById<HorizontalScrollView>(R.id.rowScroll)
        // remove listener para evitar callbacks de views fora da tela
        row?.setOnScrollChangeListener(null)
        // limpa refs mortas
        rowScrolls.removeAll { it.get() == null }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemGanttRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Etapa>() {
            override fun areItemsTheSame(old: Etapa, new: Etapa) = old.id == new.id
            override fun areContentsTheSame(old: Etapa, new: Etapa) = old == new
        }
    }
}