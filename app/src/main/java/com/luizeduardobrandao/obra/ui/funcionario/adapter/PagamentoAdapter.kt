package com.luizeduardobrandao.obra.ui.funcionario.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Pagamento
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class PagamentoAdapter(
    private val showDelete: Boolean = true,
    private val showEdit: Boolean = true,
    private val onDeleteClick: (Pagamento) -> Unit = {},
    private val onEditClick: (Pagamento) -> Unit = {}
) : ListAdapter<Pagamento, PagamentoAdapter.PagamentoVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Pagamento>() {
            override fun areItemsTheSame(oldItem: Pagamento, newItem: Pagamento): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Pagamento, newItem: Pagamento): Boolean =
                oldItem == newItem
        }

        private val LOCALE_BR = Locale("pt", "BR")
        private val CURRENCY_BR: NumberFormat = NumberFormat.getCurrencyInstance(LOCALE_BR)

        // yyyy-MM-dd -> dd/MM/yyyy
        private val SDF_ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val SDF_BR = SimpleDateFormat("dd/MM/yyyy", LOCALE_BR)

        fun formatDate(iso: String): String {
            if (iso.isBlank()) return "—"
            return try {
                val d = SDF_ISO.parse(iso)
                if (d != null) SDF_BR.format(d) else "—"
            } catch (_: ParseException) {
                if (iso.length == 10 && iso[4] == '-' && iso[7] == '-') {
                    "${iso.substring(8, 10)}/${iso.substring(5, 7)}/${iso.substring(0, 4)}"
                } else iso
            }
        }

        fun formatMoneyBR(value: Double): String = CURRENCY_BR.format(value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagamentoVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pagamento, parent, false)
        return PagamentoVH(view)
    }

    override fun onBindViewHolder(holder: PagamentoVH, position: Int) {
        val item = getItem(position)
        holder.bind(item, showDelete, showEdit, onDeleteClick, onEditClick)

        // Divider: visível exceto no último item
        val isLast = position == itemCount - 1
        holder.divider.visibility = if (isLast) View.GONE else View.VISIBLE
    }

    class PagamentoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvValor: TextView = itemView.findViewById(R.id.tvValorPagamento)
        private val tvData: TextView = itemView.findViewById(R.id.tvDataPagamento)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeletePagamento)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditPagamento)
        val divider: View = itemView.findViewById(R.id.dividerPagamento)

        fun bind(
            p: Pagamento,
            showDelete: Boolean,
            showEdit: Boolean,
            onDeleteClick: (Pagamento) -> Unit,
            onEditClick: (Pagamento) -> Unit
        ) {
            tvValor.text = formatMoneyBR(p.valor)
            tvData.text = formatDate(p.data)

            if (showDelete) {
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener { onDeleteClick(p) }
            } else {
                btnDelete.visibility = View.GONE
                btnDelete.setOnClickListener(null)
            }

            if (showEdit) {
                btnEdit.visibility = View.VISIBLE
                btnEdit.setOnClickListener { onEditClick(p) }
            } else {
                btnEdit.visibility = View.GONE
                btnEdit.setOnClickListener(null)
            }
        }
    }
}