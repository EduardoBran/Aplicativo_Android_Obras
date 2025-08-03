package com.luizeduardobrandao.obra.ui.cronograma.adapter

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
    private val onEdit:   (Etapa) -> Unit = {},
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

            // ② Descrição (trata nulo ou em branco)
            b.tvDescEtapa.text = if (e.descricao.isNullOrBlank()) {
                "—"
            } else {
                e.descricao
            }

            // ③ Datas (usa string resource com placeholders)
            b.tvDatasEtapa.text = ctx.getString(
                R.string.cronograma_date_range,
                e.dataInicio.orEmpty(),
                e.dataFim.orEmpty()
            )

            // ④ Status com cor
            b.tvStatusEtapa.apply {
                text = e.status.orEmpty()
                val colorRes = when (e.status) {
                    CronogramaPagerAdapter.STATUS_PENDENTE  -> R.color.md_theme_light_error
                    CronogramaPagerAdapter.STATUS_ANDAMENTO -> R.color.warning
                    else                                    -> R.color.success
                }
                setTextColor(ContextCompat.getColor(ctx, colorRes))
            }

            // ⑤ Call-backs
            b.btnEditEtapa.setOnClickListener   { onEdit(e) }
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