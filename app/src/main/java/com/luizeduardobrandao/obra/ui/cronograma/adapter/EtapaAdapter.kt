package com.luizeduardobrandao.obra.ui.cronograma.adapter

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.databinding.ItemCronogramaBinding
import com.luizeduardobrandao.obra.utils.GanttUtils
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * Adapter para a lista de etapas do cronograma.
 *
 * Call-backs (edit / detail / delete) são injetadas pelo fragmento.
 */
class EtapaAdapter(
    private val getFuncionarios: () -> List<Funcionario>,   // << NOVO
    private val onEdit: (Etapa) -> Unit = {},
    private val onDetail: (Etapa) -> Unit = {},
    private val onDelete: (Etapa) -> Unit = {}
) : ListAdapter<Etapa, EtapaAdapter.VH>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id.hashCode().toLong()

    // --------- Helpers de valor (mesmas regras do Gantt) ----------
    private fun parseCsvNomes(csv: String?): List<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private val nfBR: java.text.NumberFormat =
        java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private fun formatMoneyBR(v: Double?): String =
        if (v == null) "-" else nfBR.format(v)

    /** Regras (NÃO conta domingos):
     * - Empresa: usa empresaValor (se null → "-")
     * - Funcionários:
     *   - Diária:  diasUteis * salário
     *   - Semanal: ceil(diasUteis / 7.0)  * salário
     *   - Mensal:  ceil(diasUteis / 30.0) * salário
     *   - Tarefeiro: salário (uma vez)
     */
    private fun computeValorTotal(etapa: Etapa): Double? {
        if (etapa.responsavelTipo == "EMPRESA") {
            return etapa.empresaValor
        }

        val nomes = parseCsvNomes(etapa.funcionarios)
        if (etapa.responsavelTipo != "FUNCIONARIOS" || nomes.isEmpty()) return null

        val ini = GanttUtils.brToLocalDateOrNull(etapa.dataInicio)
        val fim = GanttUtils.brToLocalDateOrNull(etapa.dataFim)
        if (ini == null || fim == null || fim.isBefore(ini)) return null

        val diasUteis = GanttUtils.daysBetween(ini, fim).count { !GanttUtils.isSunday(it) }
        if (diasUteis <= 0) return 0.0

        val funcionarios = getFuncionarios()
        if (funcionarios.isEmpty()) return null

        var total = 0.0
        nomes.forEach { nomeSel ->
            val f = funcionarios.firstOrNull { it.nome.trim().equals(nomeSel, ignoreCase = true) }
                ?: return@forEach

            val salario = f.salario.coerceAtLeast(0.0)
            val tipo = f.formaPagamento.trim().lowercase(Locale.getDefault())

            total += when {
                tipo.contains("diária") ||
                        tipo.contains("diaria") ||
                        tipo.contains("Diária") ||
                        tipo.contains("Diaria") -> diasUteis * salario

                tipo.contains("semanal") ||
                        tipo.contains("Semanal") -> ceil(diasUteis / 7.0).toInt() * salario

                tipo.contains("mensal") ||
                        tipo.contains("Mensal") -> ceil(diasUteis / 30.0).toInt() * salario

                tipo.contains("tarefeiro") ||
                        tipo.contains("tarefa") ||
                        tipo.contains("Terefeiro") -> salario

                else -> 0.0
            }
        }
        return total
    }

    /*──────────────────── ViewHolder ────────────────────*/
    inner class VH(private val b: ItemCronogramaBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(e: Etapa) {
            val ctx = b.root.context

            // ① Título
            b.tvTituloEtapa.text = e.titulo

            // ② Descrição (limite 15 chars + "…") com "Descrição:" em negrito
            val desc = e.descricao.orEmpty().trim()
            val descText = when {
                desc.isBlank() -> "—"
                desc.length > 20 -> desc.take(20) + " ..."
                else -> desc
            }
            val descPrefix = ctx.getString(R.string.cronograma_descricao_prefix) // "Descrição: "
            b.tvDescEtapa.text = SpannableStringBuilder("$descPrefix $descText").apply {
                setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0,
                    descPrefix.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // ③ Responsável (Funcionários OU Empresa)
            if (e.responsavelTipo == "EMPRESA") {
                val prefix = ctx.getString(R.string.cronograma_empresa_prefix) // "Empresa:"
                val nome = e.empresaNome.orEmpty().trim()
                val shown = if (nome.isBlank()) "—" else {
                    if (nome.length > 20) nome.take(20) + " ..." else nome
                }

                b.tvFuncsEtapa.text = SpannableStringBuilder("$prefix $shown").apply {
                    setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0,
                        prefix.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                // Funcionário(s)
                val funcionarios = e.funcionarios.orEmpty().trim()
                val funcPrefix = if (funcionarios.contains(",")) {
                    ctx.getString(R.string.cronograma_funcionarios_prefix) // "Funcionários:"
                } else {
                    ctx.getString(R.string.cronograma_funcionario_prefix)  // "Funcionário:"
                }

                val funcText = when {
                    funcionarios.isBlank() -> "—"
                    funcionarios.length > 20 -> funcionarios.take(20) + " ..."
                    else -> funcionarios
                }

                b.tvFuncsEtapa.text = SpannableStringBuilder("$funcPrefix $funcText").apply {
                    setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0,
                        funcPrefix.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // ④ Datas
            val ini = GanttUtils.brToLocalDateOrNull(e.dataInicio)
            val fim = GanttUtils.brToLocalDateOrNull(e.dataFim)
            val fmtIni = ini?.format(DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "—"
            val fmtFim = fim?.format(DateTimeFormatter.ofPattern("dd/MM/yy")) ?: "—"

            b.tvDatasEtapa.text = ctx.getString(
                R.string.cronograma_date_range,
                fmtIni,
                fmtFim
            )

            // ④.1 Valor (NOVO) — mesmas regras do Gantt; mostra "Valor: R$X" ou "Valor: -"
            val valorTotal = computeValorTotal(e)
            b.tvValorEtapa.text = ctx.getString(
                R.string.gantt_valor_prefix,
                if (valorTotal == null) "-" else formatMoneyBR(valorTotal)
            )

            // ⑤ Status com cor
            b.tvStatusEtapa.apply {
                text = e.status
                val colorRes = when (e.status) {
                    CronogramaPagerAdapter.STATUS_PENDENTE -> R.color.md_theme_light_error
                    CronogramaPagerAdapter.STATUS_ANDAMENTO -> R.color.warning
                    else -> R.color.success
                }
                setTextColor(ContextCompat.getColor(ctx, colorRes))
            }

            // ⑥ Call-backs
            b.btnEditEtapa.setOnClickListener { onEdit(e) }
            b.btnDetailEtapa.setOnClickListener { onDetail(e) }
            b.btnDeleteEtapa.setOnClickListener { onDelete(e) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            ItemCronogramaBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Etapa>() {
            override fun areItemsTheSame(old: Etapa, new: Etapa) =
                old.id == new.id

            override fun areContentsTheSame(old: Etapa, new: Etapa) =
                old == new
        }
    }
}