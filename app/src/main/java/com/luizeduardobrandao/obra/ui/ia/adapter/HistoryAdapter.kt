package com.luizeduardobrandao.obra.ui.ia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.data.model.SolutionHistory
import com.luizeduardobrandao.obra.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val onClick: (SolutionHistory) -> Unit,
    private val onDelete: (SolutionHistory) -> Unit
) : ListAdapter<SolutionHistory, HistoryAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SolutionHistory) = with(b) {
            tvTitle.text = item.title
            tvDate.text = item.date
            root.setOnClickListener { onClick(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SolutionHistory>() {
            override fun areItemsTheSame(o: SolutionHistory, n: SolutionHistory) = o.id == n.id
            override fun areContentsTheSame(o: SolutionHistory, n: SolutionHistory) = o == n
        }
    }
}