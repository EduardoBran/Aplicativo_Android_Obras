package com.luizeduardobrandao.obra.ui.resumo.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Aporte
import com.luizeduardobrandao.obra.databinding.ItemAporteBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import android.view.View

class AporteAdapter(
    private val onDeleteClick: (Aporte) -> Unit
) : ListAdapter<Aporte, AporteAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Aporte>() {
            override fun areItemsTheSame(oldItem: Aporte, newItem: Aporte) =
                oldItem.aporteId == newItem.aporteId

            override fun areContentsTheSame(oldItem: Aporte, newItem: Aporte) =
                oldItem == newItem
        }
    }

    inner class VH(private val bind: ItemAporteBinding) : RecyclerView.ViewHolder(bind.root) {

        // BRL mask
        private val currency = NumberFormat.getCurrencyInstance(
            Locale("pt", "BR")
        )

        // entrada: ISO yyyy-MM-dd -> saída: dd/MM/yyyy
        private val inFmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        private val outFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        fun bind(item: Aporte, showDivider: Boolean) = with(bind) {
            tvAporteValor.text = currency.format(item.valor)

            // Tenta converter ISO -> dd/MM/yyyy; se falhar, exibe como veio
            val dateText = try {
                val ld = java.time.LocalDate.parse(item.data, inFmt)
                ld.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            } catch (_: Exception) {
                item.data
            }
            tvAporteData.text = dateText

            tvAporteDescricao.text =
                if (item.descricao.isBlank()) "-" else item.descricao

            btnDeleteAporte.setOnClickListener {
                onDeleteClick(item)
            }

            // 👇 controla o divisor do item
            dividerAporte.visibility = if (showDivider) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAporteBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        // Esconde o divisor no último item
        val isLast = position == itemCount - 1
        val showDivider = itemCount > 1 && !isLast
        holder.bind(getItem(position), showDivider)
    }
}