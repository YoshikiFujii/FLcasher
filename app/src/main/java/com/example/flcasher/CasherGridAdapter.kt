package com.example.flcasher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.flcasher.data.model.Product
import com.example.flcasher.databinding.ItemProductGridBinding

class CasherGridAdapter(
    private val onProductClick: (Product) -> Unit,
    private val onProductLongClick: ((Product) -> Unit)? = null
) : ListAdapter<Product, CasherGridAdapter.GridViewHolder>(ProductAdapter.DiffCallback) {

    var serverIp: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val binding = ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GridViewHolder(private val binding: ItemProductGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.tvName.text = product.name
            binding.tvPrice.text = "¥${product.price}"
            
            val fileName = java.io.File(product.imageUri ?: "").name
            if (serverIp.isNotEmpty() && product.imageUri?.isNotEmpty() == true) {
                val imageUrl = "http://$serverIp:8080/images/$fileName"
                com.bumptech.glide.Glide.with(binding.root)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.color.darker_gray)
                    .into(binding.ivProductImage)
            } else {
                binding.ivProductImage.setImageResource(0)
                binding.root.setCardBackgroundColor(getColorForProduct(product.name))
            }

            binding.root.setOnClickListener { onProductClick(product) }
            binding.root.setOnLongClickListener { 
                onProductLongClick?.invoke(product)
                true 
            }
        }
    }
    
    private fun getColorForProduct(name: String): Int {
        val hash = name.hashCode()
        val r = (hash and 0xFF0000 shr 16) / 2 + 127
        val g = (hash and 0x00FF00 shr 8) / 2 + 127
        val b = (hash and 0x0000FF) / 2 + 127
        return Color.rgb(r, g, b)
    }
}
