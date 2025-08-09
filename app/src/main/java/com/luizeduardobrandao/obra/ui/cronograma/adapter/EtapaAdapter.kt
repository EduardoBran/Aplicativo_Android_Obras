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
            b.tvTituloEtapa.text = e.titulo.orEmpty()

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

            // ③ Funcionários com "Funcionário:" ou "Funcionários:" em negrito + limite de 25 chars
            val funcionarios = e.funcionarios.orEmpty().trim()
            val funcPrefix = if (funcionarios.contains(",")) {
                ctx.getString(R.string.cronograma_funcionarios_prefix) // "Funcionários:"
            } else {
                ctx.getString(R.string.cronograma_funcionario_prefix)  // "Funcionário:"
            }

            // Aplica limite de 25 caracteres no texto
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

            // ④ Datas
            b.tvDatasEtapa.text = ctx.getString(
                R.string.cronograma_date_range,
                e.dataInicio.orEmpty(),
                e.dataFim.orEmpty()
            )

            // ⑤ Status com cor
            b.tvStatusEtapa.apply {
                text = e.status.orEmpty()
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