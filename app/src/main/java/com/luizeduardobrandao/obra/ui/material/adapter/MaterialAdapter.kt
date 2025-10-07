package com.luizeduardobrandao.obra.ui.material.adapter

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.databinding.ItemMaterialBinding

/**
 * Adapter para a lista de materiais.
 * As ações (editar / detalhar / excluir) são injetadas via callbacks.
 */

class MaterialAdapter(
    private val onEdit: (Material) -> Unit = {},
    private val onDetail: (Material) -> Unit = {},
    private val onDelete: (Material) -> Unit = {},
    private val showActions: Boolean = true,
    var showDelete: Boolean = true
) : ListAdapter<Material, MaterialAdapter.VH>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id.hashCode().toLong()

    /*──────────────────── ViewHolder ────────────────────*/
    inner class VH(private val b: ItemMaterialBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(m: Material) = with(b) {
            val ctx = root.context

            // ① Nome
            tvNomeMaterial.text = m.nome

            // ② Status com cor
            tvStatusMaterial.apply {
                text = m.status
                val colorRes = if (m.status.equals(MaterialPagerAdapter.STATUS_ATIVO, true))
                    R.color.success else R.color.md_theme_light_error
                setTextColor(ContextCompat.getColor(ctx, colorRes))
            }

            // ③ Descrição (resumo com limite e prefixo em negrito opcional)
            val desc = m.descricao.orEmpty().trim()
            val descText = when {
                desc.isBlank() -> "—"
                desc.length > 40 -> desc.take(40) + " ..."
                else -> desc
            }
            tvDescMaterial.text = descText

            // ④ Quantidade com prefixo em negrito ("Quantidade")
            val prefix = ctx.getString(R.string.material_label_quantity)
            tvQtdMaterial.text = SpannableStringBuilder("$prefix: ${m.quantidade}").apply {
                setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    0,
                    prefix.length + 1, // inclui o ':'
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // ⑤ Ações
            if (showActions) {
                layoutActions.visibility = View.VISIBLE
                dividerActions.visibility = View.VISIBLE

                btnEditMaterial.setOnClickListener { onEdit(m) }
                btnDetailMaterial.setOnClickListener { onDetail(m) }

                btnDeleteMaterial.visibility = if (showDelete) View.VISIBLE else View.GONE
                btnDeleteMaterial.setOnClickListener(
                    if (showDelete) View.OnClickListener { onDelete(m) } else null
                )
            } else {
                layoutActions.visibility = View.GONE
                dividerActions.visibility = View.GONE
                btnEditMaterial.setOnClickListener(null)
                btnDetailMaterial.setOnClickListener(null)
                btnDeleteMaterial.setOnClickListener(null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMaterialBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Material>() {
            override fun areItemsTheSame(old: Material, new: Material) =
                old.id == new.id

            override fun areContentsTheSame(old: Material, new: Material) =
                old == new
        }
    }
}