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
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.databinding.ItemGanttRowBinding
import com.luizeduardobrandao.obra.utils.GanttUtils
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GanttRowAdapter(
    private val onToggleDay: (Etapa, Set<String>) -> Unit,
    private val requestHeaderDays: () -> List<LocalDate>,
    private val getFuncionarios: () -> List<Funcionario> // << NOVO
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

    // ---- Helpers de funcionários/valor ----
    private fun parseCsvNomes(csv: String?): List<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private val nfBR: java.text.NumberFormat =
        java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))

    private fun formatMoneyBR(v: Double?): String =
        if (v == null) "-" else nfBR.format(v)

    /**
     * Regras (NÃO conta domingos):
     * - Diária: diasUteis * salario
     * - Semanal: ceil(diasUteis / 7.0) * salario
     * - Mensal:  ceil(diasUteis / 30.0) * salario
     * - Tarefeiro: salario (uma vez)
     * diasUteis = dias entre início e fim (INCLUSIVO) excluindo domingos.
     */
    private fun computeValorTotal(etapa: Etapa): Double? {
        // Empresa → usa o valor informado (pode ser null)
        if (etapa.responsavelTipo == "EMPRESA") {
            return etapa.empresaValor
        }

        // Funcionários precisam estar selecionados
        val nomes = parseCsvNomes(etapa.funcionarios)
        if (etapa.responsavelTipo != "FUNCIONARIOS" || nomes.isEmpty()) return null

        val ini = GanttUtils.brToLocalDateOrNull(etapa.dataInicio)
        val fim = GanttUtils.brToLocalDateOrNull(etapa.dataFim)
        if (ini == null || fim == null || fim.isBefore(ini)) return null

        // conta EXCLUINDO domingos
        val diasUteis = GanttUtils.daysBetween(ini, fim).count { !GanttUtils.isSunday(it) }
        if (diasUteis <= 0) return 0.0

        val funcionarios = getFuncionarios()
        if (funcionarios.isEmpty()) return null

        var total = 0.0
        nomes.forEach { nomeSel ->
            val f = funcionarios.firstOrNull { it.nome.trim().equals(nomeSel, ignoreCase = true) }
            if (f != null) {
                val salario = f.salario.coerceAtLeast(0.0)
                val tipo = f.formaPagamento.trim().lowercase()

                total += when {
                    tipo.contains("diária") || tipo.contains("diaria")
                            || tipo.contains("Diária") || tipo.contains("Diaria") -> {
                        diasUteis * salario
                    }

                    tipo.contains("semanal") || tipo.contains("Semanal") -> {
                        kotlin.math.ceil(diasUteis / 7.0).toInt() * salario
                    }

                    tipo.contains("mensal") || tipo.contains("Mensal") -> {
                        kotlin.math.ceil(diasUteis / 30.0).toInt() * salario
                    }

                    tipo.contains("tarefeiro") || tipo.contains("tarefa")
                            || tipo.contains("Terefeiro") -> {
                        salario
                    }

                    else -> 0.0
                }
            }
        }
        return total
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

            // Valor total do cronograma conforme regras (sem domingos)
            val valorTotal = computeValorTotal(etapa)
            b.tvValor.text = itemView.context.getString(
                R.string.gantt_valor_prefix,
                if (valorTotal == null) "-" else formatMoneyBR(valorTotal)
            )

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