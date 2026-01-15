package com.example.flcasher

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.flcasher.databinding.ActivityKitchenBinding
import com.example.flcasher.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KitchenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKitchenBinding
    private lateinit var adapter: KitchenAdapter
    private val networkClient = NetworkClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKitchenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnConnect.setOnClickListener {
            showIpDialog()
        }
        
        binding.btnHistory.setOnCheckedChangeListener { _, isChecked ->
            fetchOrders()
            binding.btnHistory.text = if (isChecked) "History Mode" else "Live Mode"
        }
        
        // Auto-show dialog on start
        showIpDialog()
    }

    private fun setupRecyclerView() {
        adapter = KitchenAdapter { order ->
             // Handle completion or revert
             val isHistory = binding.btnHistory.isChecked
             
             lifecycleScope.launch {
                 if (isHistory && order.order.status == "COMPLETED") {
                     // Revert
                     val success = networkClient.revertOrder(order.order.id)
                     if (success) {
                         Toast.makeText(this@KitchenActivity, "Order Reverted to Pending", Toast.LENGTH_SHORT).show()
                         fetchOrders()
                     }
                 } else if (!isHistory && order.order.status != "COMPLETED") {
                     // Complete
                     val success = networkClient.completeOrder(order.order.id)
                     if (success) {
                         Toast.makeText(this@KitchenActivity, "Order Completed", Toast.LENGTH_SHORT).show()
                         fetchOrders()
                     }
                 }
             }
        }
        // 2 columns for tablet feel
        binding.rvKitchenOrders.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.rvKitchenOrders.adapter = adapter
    }

    private fun showIpDialog() {
        val prefs = getSharedPreferences("FLCasherPrefs", MODE_PRIVATE)
        val savedIp = prefs.getString("SERVER_IP", "")
        if (savedIp?.isNotEmpty() == true) {
             networkClient.serverIp = savedIp
        }

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(networkClient.serverIp)

        AlertDialog.Builder(this)
            .setTitle("Enter Admin/Server IP")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val ip = input.text.toString()
                if (ip.isNotBlank()) {
                    networkClient.serverIp = ip
                    prefs.edit().putString("SERVER_IP", ip).apply() // Save
                    
                    connectToWebSocket()
                    fetchOrders()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectToWebSocket() {
        lifecycleScope.launch(Dispatchers.IO) {
            networkClient.connectToKitchenWebSocket().collect { message ->
                withContext(Dispatchers.Main) {
                    // Check message type for sound
                    try {
                        val json = org.json.JSONObject(message)
                        val type = json.optString("type")
                        if (type == "NEW_ORDER") {
                             // Play Alert Sound (Bell/Notification)
                            val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                            val r = android.media.RingtoneManager.getRingtone(applicationContext, notification)
                            r.play()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val isHistory = binding.btnHistory.isChecked
                    if (!isHistory) {
                        fetchOrders() // Always refresh for any update (complete/revert/new)
                    }
                }
            }
        }
    }

    private fun fetchOrders() {
        lifecycleScope.launch {
            val isHistory = binding.btnHistory.isChecked
            val orders = if (isHistory) {
                networkClient.getKitchenHistory()
            } else {
                networkClient.getKitchenOrders()
            }
            adapter.submitList(orders)
        }
    }
    
    override fun onResume() {
        super.onResume()
        adapter.startTimer()
    }
    
    override fun onPause() {
        super.onPause()
        adapter.stopTimer()
    }
}
