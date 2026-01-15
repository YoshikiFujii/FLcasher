package com.example.flcasher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.flcasher.data.model.OrderWithItems
import com.example.flcasher.databinding.ItemOrderHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

    class OrderHistoryAdapter(private val onDeleteClick: (OrderWithItems) -> Unit) : ListAdapter<OrderWithItems, OrderHistoryAdapter.HistoryViewHolder>(DiffCallback) {

    private val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemOrderHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(private val binding: ItemOrderHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OrderWithItems) {
            val order = item.order
            val idText = if (order.randomId != null) "Order #${order.displayId} (${order.randomId})" else "Order #${order.id}"
            binding.tvOrderId.text = idText
            binding.tvOrderStatus.text = order.status
            binding.tvOrderTime.text = sdf.format(Date(order.timestamp))
            binding.tvOrderTotal.text = "Total: ¥${order.totalAmount}"

            // Status Color
            if (order.status == "COMPLETED") {
                binding.tvOrderStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            } else {
                binding.tvOrderStatus.setTextColor(Color.parseColor("#FF9800")) // Orange
            }

            // Items Summary
            val sb = StringBuilder()
            item.items.forEach { 
                sb.append("• ${it.productName} x${it.quantity}\n")
            }
            binding.tvOrderItems.text = sb.toString().trim()
            
            // Delete Click
            binding.btnDeleteOrder.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<OrderWithItems>() {
        override fun areItemsTheSame(oldItem: OrderWithItems, newItem: OrderWithItems) = oldItem.order.id == newItem.order.id
        override fun areContentsTheSame(oldItem: OrderWithItems, newItem: OrderWithItems) = oldItem == newItem
    }
}
