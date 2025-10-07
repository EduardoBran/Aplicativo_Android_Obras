package com.luizeduardobrandao.obra.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.obra.databinding.ItemActionListBinding

class HomeAdapter(
    private val onClick: (HomeAction.Id) -> Unit
) : ListAdapter<HomeAction, HomeAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<HomeAction>() {
        override fun areItemsTheSame(oldItem: HomeAction, newItem: HomeAction) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: HomeAction, newItem: HomeAction) =
            oldItem == newItem
    }

    inner class VH(private val b: ItemActionListBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: HomeAction) = with(b) {
            ivIcon.setImageResource(item.icon)
            tvTitle.setText(item.title)

            if (item.subtitle != null) {
                tvSubtitle.isGone = false
                tvSubtitle.setText(item.subtitle)
            } else {
                tvSubtitle.isGone = true
            }

            // Animação de entrada visível e suave
            b.root.alpha = 0f
            b.root.translationY = -60f

            val delay =
                (bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: 0) * 20L
            b.root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(900)
                .setStartDelay(delay)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            root.setOnClickListener { onClick(item.id) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemActionListBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))
}