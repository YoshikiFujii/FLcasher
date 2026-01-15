package com.example.flcasher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.flcasher.data.model.Product
import com.example.flcasher.databinding.ItemCartBinding

class CartAdapter(
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val items = mutableListOf<Pair<Product, Int>>()

    fun submitList(newItems: List<Pair<Product, Int>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val (product, quantity) = items[position]
        holder.bind(product, quantity, position)
    }

    override fun getItemCount() = items.size

    inner class CartViewHolder(private val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product, quantity: Int, position: Int) {
            val totalItemPrice = product.price * quantity
            binding.tvCartName.text = product.name
            binding.tvCartPrice.text = "¥$totalItemPrice"
            binding.tvCartQuantity.text = "x$quantity"
            
            binding.btnRemove.setOnClickListener {
                onDelete(position)
            }
        }
    }
}
