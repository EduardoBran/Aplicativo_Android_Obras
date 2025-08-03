package com.luizeduardobrandao.obra.ui.notas.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.databinding.ItemNotaBinding

/**
 * Adapter de Notas – usado nas listas das abas “A Pagar” e “Pago”.
 *
 * • Usa DiffUtil para atualização eficiente.
 * • Recebe 3 lambdas (editar, detalhar, excluir) do fragmento pai.
 */

class NotaAdapter(
    private val onEdit: (Nota) -> Unit = {},
    private val onDetail: (Nota) -> Unit = {},
    private val onDelete: (Nota) -> Unit = {}
) : ListAdapter<Nota, NotaAdapter.VH>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id.hashCode().toLong()

    inner class VH(private val b: ItemNotaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(nota: Nota) = with(b) {
            tvNomeMaterial.text = nota.nomeMaterial
            tvLoja.text = nota.loja
            tvValor.text = b.root.context.getString(R.string.money_mask, nota.valor)

            tvStatus.apply {
                text = nota.status
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (nota.status == "A Pagar")
                            R.color.md_theme_light_error
                        else
                            R.color.success            // verde para “Pago”
                    )
                )
            }

            // Ações
            btnEditNota.setOnClickListener { onEdit(nota) }
            btnDetailNota.setOnClickListener { onDetail(nota) }
            btnDeleteNota.setOnClickListener { onDelete(nota) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemNotaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Nota>() {
            override fun areItemsTheSame(old: Nota, new: Nota) = old.id == new.id
            override fun areContentsTheSame(old: Nota, new: Nota) = old == new
        }
    }
}