package com.example.flcasher

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.flcasher.network.ServerManager

class AdminActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    private var isServerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        tvStatus = findViewById(R.id.tv_server_status)
        
        findViewById<android.widget.Button>(R.id.btn_manage_products).setOnClickListener {
            startActivity(android.content.Intent(this, ProductActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.btn_sales_report).setOnClickListener {
            startActivity(android.content.Intent(this, SalesActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btn_order_history).setOnClickListener {
            startActivity(android.content.Intent(this, OrderHistoryActivity::class.java))
        }
        
        val btnToggle = findViewById<android.widget.Button>(R.id.btn_toggle_server)
        btnToggle.setOnClickListener {
            if (isServerRunning) {
                stopServerService()
            } else {
                startServerService()
            }
        }

        findViewById<android.widget.Button>(R.id.btn_reset_db).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset Database?")
                .setMessage("This will delete all products and orders. Use this if the app is crashing.")
                .setPositiveButton("Reset") { _, _ ->
                    stopServerService()
                    // Delete DB
                    val dbName = "flcasher_database"
                    if (deleteDatabase(dbName)) {
                        android.widget.Toast.makeText(this, "Database Reset. Please restart app.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(this, "Failed to reset. Try clearing app data in Settings.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Initially check? For now assume stopped or let user start.
        // Or if we want to detect if service is running...
        // For simplicity, default to stopped on app launch (unless we implemented persistent state).
        // If the service IS running, user can just click Start again (it re-binds) or Stop.
        // Ideally we would check `isServiceRunning` util.
        updateToggleButton()
    }

    private fun startServerService() {
        try {
            val intent = android.content.Intent(this, com.example.flcasher.network.ServerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            val ip = getIpAddress()
            tvStatus.text = "Server Service Running at http://$ip:8080"
            isServerRunning = true
            updateToggleButton()
        } catch (e: Exception) {
            tvStatus.text = "Failed to start service: ${e.message}"
            e.printStackTrace()
        }
    }
    
    private fun stopServerService() {
        try {
             val intent = android.content.Intent(this, com.example.flcasher.network.ServerService::class.java)
             stopService(intent)
             tvStatus.text = "Server Stopped"
             isServerRunning = false
             updateToggleButton()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateToggleButton() {
        val btnToggle = findViewById<android.widget.Button>(R.id.btn_toggle_server)
        if (isServerRunning) {
            btnToggle.text = "Stop Server"
            btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E53935")) // Red
        } else {
            btnToggle.text = "Start Server"
            btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#43A047")) // Green
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional: Keep service running even if AdminActivity is destroyed?
        // User requested "always running even if app isn't on screen".
        // So we should NOT stop service in onDestroy unless explicitly requested.
        // If we want to kill it, we'd add a "Stop Server" button.
        // For now, removing stopServer() so it stays alive.
    }

    private fun getIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    }
}
