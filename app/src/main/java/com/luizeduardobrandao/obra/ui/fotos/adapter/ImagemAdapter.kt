package com.luizeduardobrandao.obra.ui.fotos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Imagem

class ImagemAdapter(
    private val onOpen: (Imagem) -> Unit,
    private val onExpand: (Imagem) -> Unit
) : ListAdapter<Imagem, ImagemAdapter.ImgVH>(Diff) {

    init {
        setHasStableIds(true)
    }

    object Diff : DiffUtil.ItemCallback<Imagem>() {
        override fun areItemsTheSame(old: Imagem, new: Imagem) = old.id == new.id
        override fun areContentsTheSame(old: Imagem, new: Imagem) = old == new
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImgVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_imagem, parent, false)
        return ImgVH(v)
    }

    override fun onBindViewHolder(holder: ImgVH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id.hashCode().toLong()

    inner class ImgVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: ImageView = itemView.findViewById(R.id.imgThumb)
        private val btnExpand: ImageButton = itemView.findViewById(R.id.btnExpand)
        private val tvNome: TextView = itemView.findViewById(R.id.tvNome)
        private val tvData: TextView = itemView.findViewById(R.id.tvData)

        fun bind(item: Imagem) {
            // Nome e Data
            tvNome.text = item.nome
            tvData.text = item.data ?: ""

            // Miniatura
            // Prioriza uma URL de thumb se você tiver. Aqui usamos a url principal.
            imgThumb.load(item.fotoUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_broken_image)
            }

            // Clicks
            itemView.setOnClickListener { onOpen(item) }
            imgThumb.setOnClickListener { onOpen(item) }
            btnExpand.setOnClickListener { onExpand(item) }
        }
    }
}