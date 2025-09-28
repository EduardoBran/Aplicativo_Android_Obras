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
import com.luizeduardobrandao.obra.databinding.ItemCronogramaBinding
import com.luizeduardobrandao.obra.utils.GanttUtils
import java.time.format.DateTimeFormatter

/**
 * Adapter para a lista de etapas do cronograma.
 *
 * Call-backs (edit / detail / delete) são injetadas pelo fragmento.
 */

class EtapaAdapter(
    private val onEdit: (Etapa) -> Unit = {},
    private val onDetail: (Etapa) -> Unit = {},
    private val onDelete: (Etapa) -> Unit = {}
) : ListAdapter<Etapa, EtapaAdapter.VH>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id.hashCode().toLong()

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
                // Mantém comportamento anterior (Funcionário(s))
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