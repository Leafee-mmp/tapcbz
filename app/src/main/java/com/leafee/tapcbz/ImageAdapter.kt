package com.leafee.tapcbz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.leafee.tapcbz.databinding.ItemImageBinding

class ImageAdapter(
    private val items: List<ImageItem>,
    private val onTap: (ImageItem) -> Unit
) : RecyclerView.Adapter<ImageAdapter.VH>() {

    var showHidden = false

    inner class VH(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.binding.root.context

        Glide.with(ctx)
            .load(item.uri)
            .centerCrop()
            .placeholder(android.R.color.darker_gray)
            .into(holder.binding.ivThumb)

        holder.binding.tvName.text = item.name

        if (item.hidden && showHidden) {
            // Shown in reveal mode — red tint, no X
            holder.binding.ivThumb.alpha = 0.4f
            holder.binding.overlay.alpha = 1f
            holder.binding.ivIgnoreIcon.alpha = 0f
        } else {
            // Normal selected state
            holder.binding.ivThumb.alpha = 1f
            holder.binding.overlay.alpha = 0f
            holder.binding.ivIgnoreIcon.alpha = 0f
        }

        holder.binding.root.setOnClickListener { onTap(item) }

        holder.binding.root.setOnLongClickListener {
            android.widget.Toast.makeText(ctx, item.name, android.widget.Toast.LENGTH_SHORT).show()
            true
        }
    }
}
