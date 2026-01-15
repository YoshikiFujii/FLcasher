package com.example.flcasher

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.flcasher.data.model.OrderWithItems
import com.example.flcasher.databinding.ItemKitchenOrderBinding
import java.util.concurrent.TimeUnit

class KitchenAdapter(
    private val onComplete: (OrderWithItems) -> Unit
) : ListAdapter<OrderWithItems, KitchenAdapter.OrderViewHolder>(DiffCallback) {

    // Hacky timer update mechanism
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            notifyDataSetChanged() // Rebind all to update timers/colors
            handler.postDelayed(this, 1000 * 60) // Update every minute
        }
    }

    fun startTimer() {
        handler.post(updateRunnable)
    }

    fun stopTimer() {
        handler.removeCallbacks(updateRunnable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemKitchenOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(private val binding: ItemKitchenOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OrderWithItems) {
            val order = item.order
            binding.tvOrderId.text = "Order #${order.id}"
            
            // Format Items
            val sb = StringBuilder()
            item.items.forEach { 
                sb.append("${it.quantity}x ${it.productName}\n")
            }
            binding.tvOrderItems.text = sb.toString()

            // Time & Color Logic
            val elapsedMillis = System.currentTimeMillis() - order.timestamp
            val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
            binding.tvTimer.text = "${elapsedMinutes}m"

            val cardColor = when {
                order.status == "COMPLETED" -> Color.LTGRAY // Visual cue for history
                elapsedMinutes >= 10 -> Color.parseColor("#FFCDD2") 
                elapsedMinutes >= 5 -> Color.parseColor("#FFF9C4") 
                else -> Color.WHITE
            }
            binding.root.setCardBackgroundColor(cardColor)

            binding.root.setOnClickListener { onComplete(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<OrderWithItems>() {
        override fun areItemsTheSame(oldItem: OrderWithItems, newItem: OrderWithItems) = oldItem.order.id == newItem.order.id
        override fun areContentsTheSame(oldItem: OrderWithItems, newItem: OrderWithItems) = oldItem == newItem
    }
}
