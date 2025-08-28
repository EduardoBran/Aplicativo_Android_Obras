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
import coil.decode.DataSource

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

    override fun onViewRecycled(holder: ImgVH) {
        holder.stopShimmer() // evita shimmer “preso” ao reciclar
        super.onViewRecycled(holder)
    }

    inner class ImgVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: ImageView = itemView.findViewById(R.id.imgThumb)
        private val btnExpand: ImageButton = itemView.findViewById(R.id.btnExpand)
        private val tvNome: TextView = itemView.findViewById(R.id.tvNome)
        private val tvData: TextView = itemView.findViewById(R.id.tvData)
        private val shimmer: com.facebook.shimmer.ShimmerFrameLayout =
            itemView.findViewById(R.id.shimmerThumb)

        private var startRunnable: Runnable? = null

        fun bind(item: Imagem) {
            tvNome.text = item.nome
            tvData.text = item.data

            // limpa estado do item reciclado
            imgThumb.setImageDrawable(null)
            stopShimmer()

            var loadedFromMemory = false

            imgThumb.load(item.fotoUrl) {
                error(R.drawable.ic_broken_image)
                crossfade(true) // Coil não aplica crossfade quando é MEMORY_CACHE

                listener(
                    onStart = {
                        // agenda o shimmer para o próximo frame; se vier do cache,
                        // onSuccess roda antes e o shimmer não chega a aparecer
                        scheduleConditionalShimmer { !loadedFromMemory }
                    },
                    onSuccess = { _, metadata ->
                        loadedFromMemory = (metadata.dataSource == DataSource.MEMORY_CACHE)
                        stopShimmer()
                    },
                    onError = { _, _ ->
                        stopShimmer()
                    }
                )
            }

            itemView.setOnClickListener { onOpen(item) }
            imgThumb.setOnClickListener { onOpen(item) }
            btnExpand.setOnClickListener { onExpand(item) }
        }

        private fun scheduleConditionalShimmer(shouldStart: () -> Boolean) {
            startRunnable?.let { itemView.removeCallbacks(it) }
            startRunnable = Runnable {
                if (shouldStart()) {
                    shimmer.visibility = View.VISIBLE
                    shimmer.alpha = 1f
                    shimmer.startShimmer()
                }
            }.also { itemView.post(it) }
        }

        fun stopShimmer() {
            startRunnable?.let { itemView.removeCallbacks(it) }
            startRunnable = null
            shimmer.stopShimmer()
            shimmer.visibility = View.GONE
            shimmer.alpha = 1f
        }
    }
}