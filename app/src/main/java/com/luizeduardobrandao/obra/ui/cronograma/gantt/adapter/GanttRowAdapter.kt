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
import java.time.format.DateTimeFormatter

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

    private val scrollXs = mutableMapOf<String, Int>()  // chave: etapa.id
    private var lastScrollX: Int = 0

    private fun propagateFromRow(source: HorizontalScrollView, scrollX: Int) {
        if (syncing) return
        syncing = true
        lastScrollX = scrollX
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
            lastScrollX = scrollX                          // <—— lembra o último scroll global
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

            // Título
            tvTitulo.text =
                etapa.titulo.ifBlank { itemView.context.getString(R.string.gantt_col_tarefa) }
            // Período (data)
            val ini = GanttUtils.brToLocalDateOrNull(etapa.dataInicio)
            val fim = GanttUtils.brToLocalDateOrNull(etapa.dataFim)
            val fmtIni = ini?.format(DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "—"
            val fmtFim = fim?.format(DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "—"

            tvPeriodo.text = itemView.context.getString(
                R.string.cronograma_date_range,
                fmtIni,
                fmtFim
            )
            // Responsável
            when (etapa.responsavelTipo) {
                "EMPRESA" -> {
                    val nome = etapa.empresaNome?.trim().orEmpty()
                    if (nome.isNotEmpty()) {
                        b.tvResponsavel.visibility = View.VISIBLE
                        b.tvResponsavel.text = itemView.context.getString(
                            R.string.cronograma_empresa_fmt, nome
                        )
                    } else {
                        b.tvResponsavel.visibility = View.GONE
                        b.tvResponsavel.text = ""
                    }
                }

                "FUNCIONARIOS" -> {
                    b.tvResponsavel.visibility = View.VISIBLE
                    b.tvResponsavel.text =
                        itemView.context.getString(R.string.cronograma_funcionarios_title)
                }

                else -> {
                    b.tvResponsavel.visibility = View.GONE
                    b.tvResponsavel.text = ""
                }
            }

            // Progresso (sempre recalculado na UI ignorando domingos)
            val pctBind = GanttUtils.calcularProgresso(
                etapa.diasConcluidos?.toSet() ?: emptySet(),
                etapa.dataInicio,
                etapa.dataFim
            )
            tvProgresso.text = itemView.context.getString(R.string.gantt_progress_fmt, pctBind)

            // ——— Timeline: cabeçalho global + range da etapa ———
            val headerDays = requestHeaderDays()
            val etapaStart = GanttUtils.brToLocalDateOrNull(etapa.dataInicio)
            val etapaEnd = GanttUtils.brToLocalDateOrNull(etapa.dataFim)

            timelineView.apply {
                setHeaderDays(headerDays)
                setEtapaRange(etapaStart, etapaEnd)
                setDiasConcluidosUtc(etapa.diasConcluidos?.toSet() ?: emptySet())
                setOnDayToggleListener { newSetUtc ->
                    // (1) memorize a posição horizontal ANTES do rebind
                    val curX = rowScroll.scrollX
                    scrollXs[etapa.id] = curX
                    lastScrollX = curX

                    // (2) feedback imediato
                    val pct =
                        GanttUtils.calcularProgresso(newSetUtc, etapa.dataInicio, etapa.dataFim)
                    tvProgresso.text = itemView.context.getString(R.string.gantt_progress_fmt, pct)

                    // (3) dispara persistência (vai rebindar a linha)
                    onToggleDay(etapa, newSetUtc)
                }
                // garante que a largura acompanhe o número de dias
                requestLayout()
                invalidate()
            }

            rowScroll.isHorizontalScrollBarEnabled = true

            // ——— Sincronização com o cabeçalho ———
            val saved = scrollXs[etapa.id] ?: lastScrollX
            rowScroll.post { rowScroll.scrollTo(saved, 0) }

            rowScrolls.add(WeakReference(rowScroll))
            rowScroll.setOnScrollChangeListener { v: View, scrollX: Int, _: Int, _: Int, _: Int ->
                // sempre memorize a posição desta linha
                scrollXs[etapa.id] = scrollX
                lastScrollX = scrollX
                if (!syncing) {
                    // se o header já existe, mantenha sincronizado
                    headerScrollRef?.get()?.scrollTo(scrollX, 0)
                    if (headerScrollRef?.get() != null) {
                        propagateFromRow(v as HorizontalScrollView, scrollX)
                    }
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
        row?.let {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val etapa = getItem(position)
                scrollXs[etapa.id] = it.scrollX          // <—— persiste
            }
            it.setOnScrollChangeListener(null)
        }
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