package com.luizeduardobrandao.obra.ui.notas.adapter

import android.view.LayoutInflater
import android.view.View
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
    private val onDelete: (Nota) -> Unit = {},
    private val showActions: Boolean = true
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
            tvValor.text = root.context.getString(R.string.money_mask, nota.valor)

            // 👉 novos "Tipos", entre loja e divisor
            val tipos = nota.tipos
            if (tipos.isNullOrEmpty()) {
                tvTipos.visibility = View.GONE
            } else {
                tvTipos.visibility = View.VISIBLE
                val tiposJoined = tipos.joinToString(", ")
                tvTipos.text = tvTipos.resources.getQuantityString(
                    R.plurals.nota_list_types,
                    tipos.size,            // seleciona "Tipo" ou "Tipos"
                    tiposJoined            // %1$s
                )
            }

            val ctx = b.root.context
            val isComprado = nota.status == NotaPagerAdapter.STATUS_A_PAGAR

            // Label mostrado (UI) mapeado a partir do valor persistido
            tvStatus.text = ctx.getString(
                if (isComprado) R.string.nota_status_purchased
                else            R.string.nota_status_paid_client
            )

            // Cores (mantém sua lógica)
            tvStatus.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (isComprado) R.color.md_theme_light_error else R.color.success
                )
            )

            tvData.apply {
                // se vier vazia por algum motivo, esconde
                if (nota.data.isNullOrBlank()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = nota.data // formato já está como dd/MM/yyyy vindo do form
                }
            }

            // Mostrar/ocultar ações + divisor
            llActions.visibility = if (showActions) View.VISIBLE else View.GONE
            dividerActions.visibility = if (showActions) View.VISIBLE else View.GONE

            // Listeners só importam se visível, mas não faz mal mantê-los
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