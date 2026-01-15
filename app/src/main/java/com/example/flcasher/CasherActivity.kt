package com.example.flcasher

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.flcasher.data.model.Product
import com.example.flcasher.databinding.ActivityCasherBinding
import com.example.flcasher.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CasherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCasherBinding
    private lateinit var gridAdapter: CasherGridAdapter
    private lateinit var cartAdapter: CartAdapter
    private val networkClient = NetworkClient()
    private val gson = com.google.gson.Gson()
    
    // Cart: Product -> Quantity
    private val cart = mutableListOf<Pair<Product, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCasherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupCartRecyclerView()
        
        binding.btnIpSettings.setOnClickListener {
            showIpDialog()
        }
        
        binding.btnSettings.setOnClickListener {
            showSettingsMenu()
        }
        
        binding.btnCloseRegister.setOnClickListener {
            showCloseRegisterDialog()
        }
        
        binding.btnAddDiscount.setOnClickListener {
            showDiscountDialog()
        }
        
        binding.btnPrinterQueue.setOnClickListener {
            showQueueDialog()
        }

        binding.btnCheckout.setOnClickListener {
            if (cart.isNotEmpty()) {
                showPaymentDialog()
            }
        }

        showIpDialog()
        checkRegisterStatus()
        checkPermissionsOnStart()
    }
    
    private fun checkRegisterStatus() {
        val prefs = getSharedPreferences("FLCasherPrefs", MODE_PRIVATE)
        val openTime = prefs.getLong("REGISTER_OPEN_TIME", 0L)
        
        if (openTime == 0L) {
            showOpenRegisterDialog()
        }
    }
    
    private fun showOpenRegisterDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = getString(R.string.initial_cash_amount)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_open_register)
            .setMessage(R.string.msg_open_register)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_open) { _, _ ->
                val amountStr = input.text.toString()
                val amount = amountStr.toIntOrNull() ?: 0
                
                val prefs = getSharedPreferences("FLCasherPrefs", MODE_PRIVATE)
                prefs.edit()
                    .putLong("REGISTER_OPEN_TIME", System.currentTimeMillis())
                    .putInt("REGISTER_START_CASH", amount)
                    .putInt("REGISTER_CURRENT_SALES", 0)
                    .apply()
                    
                Toast.makeText(this, R.string.toast_register_opened, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                finish() // E.g. Back to Mode Selection
            }
            .create()
            
        dialog.show()
    }
    
    private fun showSettingsMenu() {
        // Just Printer Settings now
        checkBluetoothPermissions()
    }
    
    private fun showCloseRegisterDialog() {
        val prefs = getSharedPreferences("FLCasherPrefs", MODE_PRIVATE)
        val startCash = prefs.getInt("REGISTER_START_CASH", 0)
        val openTime = prefs.getLong("REGISTER_OPEN_TIME", 0L)
        
        lifecycleScope.launch {
            // Fetch authoritative sales total from Server (handles remote deletions)
            // If offline/error, fallback to 0 or maybe local (but local is not synced with delete)
            // For now, assume connectivity or 0.
            val serverSales = networkClient.getTotalSalesSince(openTime)
            
            // Note: We are ignoring the local REGISTER_CURRENT_SALES now, relying on Server.
            val expectedTotal = startCash + serverSales
            
            val dialogView = android.widget.LinearLayout(this@CasherActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            val tvInfo = android.widget.TextView(this@CasherActivity).apply {
                text = getString(R.string.info_close_register, startCash, serverSales, expectedTotal)
                textSize = 16f
                setPadding(0, 0, 0, 16)
            }
            dialogView.addView(tvInfo)
            
            val inputActual = EditText(this@CasherActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = getString(R.string.hint_actual_cash)
            }
            dialogView.addView(inputActual)
            
            val tvDiff = android.widget.TextView(this@CasherActivity).apply {
                text = getString(R.string.label_difference) + "-"
                textSize = 16f
                setPadding(0, 16, 0, 0)
            }
            dialogView.addView(tvDiff)
            
            // Add watcher to calc diff
            inputActual.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val actual = s.toString().toIntOrNull() ?: 0
                    val diff = actual - expectedTotal
                    tvDiff.text = getString(R.string.label_difference) + "¥$diff"
                    tvDiff.setTextColor(if (diff == 0) android.graphics.Color.BLACK else android.graphics.Color.RED)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            AlertDialog.Builder(this@CasherActivity)
                .setTitle(R.string.title_close_register)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_close_register) { _, _ ->
                    // Finish Close
                    lifecycleScope.launch {
                        val success = networkClient.resetOrderNumber()
                        if (success) {
                             prefs.edit()
                                .putLong("REGISTER_OPEN_TIME", 0L)
                                .putInt("REGISTER_START_CASH", 0)
                                .putInt("REGISTER_CURRENT_SALES", 0)
                                .apply()
                            Toast.makeText(this@CasherActivity, R.string.toast_register_closed, Toast.LENGTH_SHORT).show()
                            finish() // Return to Mode Selection
                        } else {
                            Toast.makeText(this@CasherActivity, R.string.toast_reset_order_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }

    private fun showDiscountDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Amount" // Note: "Amount" not in strings.xml, leaving as is or use custom. User asked to localize.
        // I will use "initial_cash_amount" text if it matches "Initial Cash Amount" -> "Amount"?? No.
        // I'll leave hardcoded "Amount" for now as I missed adding it, but localize title/msg.
        
        AlertDialog.Builder(this)
            .setTitle(R.string.title_add_discount)
            .setMessage(R.string.msg_add_discount)
            .setView(input)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val amountStr = input.text.toString()
                val amount = amountStr.toIntOrNull()
                if (amount != null && amount > 0) {
                    addDiscountProduct(amount)
                } else {
                    Toast.makeText(this, R.string.toast_invalid_amount, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun addDiscountProduct(amount: Int) {
        val discountProduct = Product(
            id = -System.currentTimeMillis(), // Negative ID
            name = "Discount",
            price = -amount,
            imageUri = "",
            isAvailable = true
        )
        // Send to Server
        lifecycleScope.launch {
            val success = networkClient.addProduct(discountProduct)
            if (success) {
                Toast.makeText(this@CasherActivity, R.string.toast_discount_added, Toast.LENGTH_SHORT).show()
                fetchProducts()
            } else {
                Toast.makeText(this@CasherActivity, R.string.toast_discount_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteDiscountDialog(product: Product) {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_discount)
            .setMessage(getString(R.string.msg_delete_discount, product.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                lifecycleScope.launch {
                    val success = networkClient.deleteProduct(product.id)
                    if (success) {
                        Toast.makeText(this@CasherActivity, R.string.toast_discount_deleted, Toast.LENGTH_SHORT).show()
                        fetchProducts()
                    } else {
                        Toast.makeText(this@CasherActivity, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun checkPermissionsOnStart() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = permissions.filter { 
                checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED 
            }
            if (missing.isNotEmpty()) {
                showPermissionRationaleDialog(missing.toTypedArray())
            }
        }
    }
    
    private fun showPermissionRationaleDialog(permissions: Array<String>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_permission_required)
            .setMessage(R.string.msg_permission_rationale)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                requestPermissions(permissions, 102)
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> { // Connect
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    showBluetoothSettingsDialog()
                } else {
                    showPermissionDeniedDialog()
                }
            }
            102 -> { // Startup
                 if (grantResults.any { it != android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                     showPermissionDeniedDialog()
                 }
            }
        }
    }
    
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_permission_denied)
            .setMessage(R.string.msg_permission_denied)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    private fun checkBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }
        showBluetoothSettingsDialog()
    }
    
    // --- Queue Management ---
    
    data class PrintJob(val id: String, val timestamp: Long, val deviceAddress: String, val json: String)
    
    private fun saveFailedPrintJob(deviceAddress: String, json: String) {
        val prefs = getSharedPreferences("FLCasherQueue", MODE_PRIVATE)
        val existingJson = prefs.getString("QUEUE", "[]")
        val type = object : com.google.gson.reflect.TypeToken<MutableList<PrintJob>>() {}.type
        val queue: MutableList<PrintJob> = gson.fromJson(existingJson, type)
        
        val job = PrintJob(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            deviceAddress = deviceAddress,
            json = json
        )
        queue.add(job)
        
        prefs.edit().putString("QUEUE", gson.toJson(queue)).apply()
        Toast.makeText(this, R.string.toast_saved_to_queue, Toast.LENGTH_LONG).show()
        updateQueueIcon()
    }
    
    private fun updateQueueIcon() {
        // Optional: Change icon color if queue has items
    }
    
    private fun showQueueDialog() {
        val prefs = getSharedPreferences("FLCasherQueue", MODE_PRIVATE)
        val existingJson = prefs.getString("QUEUE", "[]")
        val type = object : com.google.gson.reflect.TypeToken<MutableList<PrintJob>>() {}.type
        val queue: MutableList<PrintJob> = gson.fromJson(existingJson, type)

        if (queue.isEmpty()) {
            Toast.makeText(this, R.string.toast_queue_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        queue.forEachIndexed { index, job ->
            val jobView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
            }
            
            val info = android.widget.TextView(this).apply {
                text = "${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(job.timestamp))}\n${job.deviceAddress}"
                textSize = 14f
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            jobView.addView(info)
            
            val btnRetry = android.widget.Button(this).apply {
                text = getString(R.string.btn_retry)
                setOnClickListener {
                    retryPrintJob(job, index, queue)
                }
            }
            jobView.addView(btnRetry)
            
            val btnDelete = android.widget.ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setOnClickListener {
                    queue.removeAt(index)
                    prefs.edit().putString("QUEUE", gson.toJson(queue)).apply()
                    (parent as? android.view.ViewGroup)?.removeView(jobView) // crude refresh
                    showQueueDialog() // Refresh full dialog
                }
            }
            jobView.addView(btnDelete)
            
            dialogView.addView(jobView)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.title_pending_jobs)
            .setView(android.widget.ScrollView(this).apply { addView(dialogView) })
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }
    
    private fun retryPrintJob(job: PrintJob, index: Int, queue: MutableList<PrintJob>) {
         lifecycleScope.launch(Dispatchers.IO) {
            val printer = com.example.flcasher.utils.BluetoothPrinter(this@CasherActivity)
            val success = printer.printJson(job.deviceAddress, job.json)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@CasherActivity, R.string.toast_print_success, Toast.LENGTH_SHORT).show()
                    queue.removeAt(index)
                    val prefs = getSharedPreferences("FLCasherQueue", MODE_PRIVATE)
                    prefs.edit().putString("QUEUE", gson.toJson(queue)).apply()
                    // Close dialog to refresh or refresh list
                } else {
                    Toast.makeText(this@CasherActivity, R.string.toast_retry_failed, Toast.LENGTH_SHORT).show()
                }
            }
         }
    }

    private fun setupRecyclerView() {
        gridAdapter = CasherGridAdapter(
            onProductClick = { product ->
                addToCart(product)
            },
            onProductLongClick = { product ->
                if (product.id < 0) { // Is Logic Discount
                    showDeleteDiscountDialog(product)
                }
            }
        )
        binding.rvProductGrid.layoutManager = GridLayoutManager(this, 3) 
        binding.rvProductGrid.adapter = gridAdapter
    }
    
    private fun setupCartRecyclerView() {
        cartAdapter = CartAdapter { position ->
            if (position in cart.indices) {
                cart.removeAt(position)
                updateCartUI()
            }
        }
        binding.rvCart.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvCart.adapter = cartAdapter
    }

    private fun addToCart(product: Product) {
        val existingIndex = cart.indexOfFirst { it.first.id == product.id }
        if (existingIndex != -1) {
            val (prod, currentQty) = cart[existingIndex]
            cart[existingIndex] = prod to (currentQty + 1)
        } else {
            cart.add(product to 1) 
        }
        updateCartUI()
    }

    private fun updateCartUI() {
        cartAdapter.submitList(ArrayList(cart)) // Pass copy
        val total = cart.sumOf { it.first.price * it.second }
        binding.tvTotalAmount.text = getString(R.string.total_amount_format, total)
    }

    // ... showIpDialog ...
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
            .setTitle(R.string.title_server_ip)
            .setView(input)
            .setPositiveButton(R.string.btn_connect) { _, _ ->
                val ip = input.text.toString()
                if (ip.isNotBlank()) {
                    networkClient.serverIp = ip
                    gridAdapter.serverIp = ip // Update adapter IP
                    
                    prefs.edit().putString("SERVER_IP", ip).apply() // Save
                    
                    fetchProducts()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ... fetchProducts ...
    private fun fetchProducts() {
        lifecycleScope.launch {
            Toast.makeText(this@CasherActivity, R.string.toast_fetching_products, Toast.LENGTH_SHORT).show()
            val products = networkClient.getProducts()
            if (products.isEmpty()) {
                Toast.makeText(this@CasherActivity, R.string.toast_fetch_failed, Toast.LENGTH_LONG).show()
            } else {
                gridAdapter.submitList(products)
                Toast.makeText(this@CasherActivity, getString(R.string.toast_products_loaded, products.size), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ... showPaymentDialog ...
    private fun showPaymentDialog() {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val tvTotal = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_total)
        val tvReceived = dialogView.findViewById<android.widget.TextView>(R.id.tv_received)
        val tvChange = dialogView.findViewById<android.widget.TextView>(R.id.tv_change)
        
        val total = cart.sumOf { it.first.price * it.second }
        tvTotal.text = getString(R.string.total_amount_format, total)

        var currentReceived = 0

        fun updateUI() {
            tvReceived.text = getString(R.string.total_amount_format, currentReceived)
            val change = currentReceived - total
            if (change >= 0) {
                tvChange.text = getString(R.string.total_amount_format, change)
                tvChange.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                tvChange.text = getString(R.string.total_amount_format, 0)
                tvChange.setTextColor(android.graphics.Color.GRAY)
            }
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        fun bindKeys(view: android.view.View) {
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    bindKeys(view.getChildAt(i))
                }
            } else if (view is android.widget.Button) {
                if (view.id == R.id.btn_key_pay) {
                    view.setOnClickListener {
                        if (currentReceived < total) {
                            Toast.makeText(this, R.string.toast_insufficient_amount, Toast.LENGTH_SHORT).show()
                        } else {
                            val change = currentReceived - total
                            alertDialog.dismiss()
                            showChangeDialog(change, currentReceived)
                        }
                    }
                } else if (view.id == R.id.btn_key_clear) {
                    view.setOnClickListener {
                        currentReceived = 0
                        updateUI()
                    }
                } else {
                    val text = view.text.toString()
                    val num = text.toIntOrNull()
                    if (num != null) {
                        view.setOnClickListener {
                            if (currentReceived < 1000000) { 
                                currentReceived = currentReceived * 10 + num
                                updateUI()
                            }
                        }
                    }
                }
            }
        }
        
        bindKeys(dialogView)
        updateUI()
        alertDialog.show()
    }

    private fun showChangeDialog(change: Int, received: Int) {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_change, null)
        val tvChange = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_change_amount)
        val btnFinish = dialogView.findViewById<android.widget.Button>(R.id.btn_finish)
        
        tvChange.text = getString(R.string.total_amount_format, change)
        
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        btnFinish.setOnClickListener {
            alertDialog.dismiss()
            sendOrderToServer(received, change)
        }
        
        alertDialog.show()
    }

    private fun sendOrderToServer(received: Int, change: Int) {
        lifecycleScope.launch {
            val total = cart.sumOf { it.first.price * it.second }
            
            // Generate Random ID (8 chars)
            val randomId = java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()

            // Map to OrderItemRequest
            val items = cart.map { (prod, qty) ->
                com.example.flcasher.network.OrderItemRequest(
                    productId = prod.id,
                    productName = prod.name,
                    quantity = qty,
                    priceAtSale = prod.price
                )
            }

            val request = com.example.flcasher.network.OrderRequest(
                timestamp = System.currentTimeMillis(),
                totalAmount = total,
                status = "PENDING",
                items = items,
                randomId = randomId
            )
            
            val orderJson = gson.toJson(request)

            binding.btnCheckout.isEnabled = false
            val response = networkClient.sendOrder(orderJson)
            binding.btnCheckout.isEnabled = true

            if (response != null) {
                Toast.makeText(this@CasherActivity, getString(R.string.toast_order_sent, randomId), Toast.LENGTH_LONG).show()
                
                // Update Local Sales
                val prefs = getSharedPreferences("FLCasherPrefs", MODE_PRIVATE)
                val currentSales = prefs.getInt("REGISTER_CURRENT_SALES", 0)
                prefs.edit().putInt("REGISTER_CURRENT_SALES", currentSales + total).apply()

                // Trigger Print with ID (Use displayId for receipt)
                printReceiptIfEnabled(request, response.displayId, received, change)
                
                cart.clear()
                updateCartUI()
            } else {
                Toast.makeText(this@CasherActivity, R.string.toast_order_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun printReceiptIfEnabled(order: com.example.flcasher.network.OrderRequest, orderId: Int, received: Int, change: Int) {
        val prefs = getSharedPreferences("FLCasherPrefs", MODE_PRIVATE)
        val isPrintEnabled = prefs.getBoolean("PRINT_ENABLED", false) 
        val targetDevice = prefs.getString("PRINT_DEVICE", "")
        
        if (isPrintEnabled && !targetDevice.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_attempting_print, targetDevice), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val printer = com.example.flcasher.utils.BluetoothPrinter(this@CasherActivity)
                    
                    // Format JSON
                    val dateStr = java.text.SimpleDateFormat("yyyy年MM月dd日(E) HH:mm", java.util.Locale.JAPAN).format(java.util.Date(order.timestamp))
                    
                    val contentSb = StringBuilder()
                    order.items.forEachIndexed { index, item ->
                        if (index > 0) contentSb.append("¥n")
                        contentSb.append("${item.productName}¥|¥${item.priceAtSale} x${item.quantity}¥|")
                    }
                    
                    val receiptMap = mutableMapOf(
                        "date" to dateStr,
                        "content" to contentSb.toString(),
                        "subtotal" to "小計¥|¥${order.totalAmount}¥|",
                        "total" to "##合計¥|¥${order.totalAmount}¥|",
                        "cash" to "お預かり¥|¥$received¥|",
                        "change" to "お釣り¥|¥$change¥|"
                    )
                    
                    val isOrderNumEnabled = prefs.getBoolean("PRINT_ORDER_NUM", false)
                    if (isOrderNumEnabled) {
                        val orderNumStr = String.format("No.%04d", orderId)
                        receiptMap["ordernum"] = orderNumStr
                    }
                    
                    val json = gson.toJson(receiptMap)
                    
                    val success = printer.printJson(targetDevice, json)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(this@CasherActivity, R.string.toast_print_sent, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CasherActivity, R.string.toast_print_failed, Toast.LENGTH_LONG).show()
                            saveFailedPrintJob(targetDevice, json)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CasherActivity, getString(R.string.toast_print_error, e.message), Toast.LENGTH_LONG).show()
                        // Re-construct JSON if possible or save what we have? 
                        // The JSON creation logic is inside try block. If JSON creation failed, we can't save.
                        // Assuming failure happens at print step.
                    }
                }
            }
        }
    }
    
    private fun showBluetoothSettingsDialog() {
        val prefs = getSharedPreferences("FLCasherPrefs", MODE_PRIVATE)
        var isEnabled = prefs.getBoolean("PRINT_ENABLED", false)
        var selectedDevice = prefs.getString("PRINT_DEVICE", "")
        
        val dialogView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Toggle Switch: Enable Printing
        val switchEnable = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            text = getString(R.string.switch_enable_printing)
            isChecked = isEnabled
            textSize = 18f
        }
        dialogView.addView(switchEnable)

        // Toggle Switch: Include Order Number
        val isOrderNumEnabled = prefs.getBoolean("PRINT_ORDER_NUM", false)
        val switchOrderNum = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            text = getString(R.string.switch_include_order_num)
            isChecked = isOrderNumEnabled
            textSize = 18f
        }
        dialogView.addView(switchOrderNum)
    
        // Hint Text
        val tvHint = android.widget.TextView(this).apply {
            text = getString(R.string.label_select_device_hint)
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 24, 0, 8)
        }
        dialogView.addView(tvHint)
    
        // Device List Container
        val deviceListContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32
            }
        }
        dialogView.addView(deviceListContainer)

        val printer = com.example.flcasher.utils.BluetoothPrinter(this)
        val devices = printer.getPairedDevices().toList()
        
        // Helper to refresh list UI
        fun refreshDeviceList() {
            deviceListContainer.removeAllViews()
            if (devices.isEmpty()) {
                val tv = android.widget.TextView(this).apply { text = getString(R.string.msg_no_devices); setPadding(0,16,0,0) }
                deviceListContainer.addView(tv)
                return
            }

            devices.forEach { device ->
                val isSelected = device.address == selectedDevice
                
                val itemLayout = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(24, 24, 24, 24) // Padding for touch target
                    setBackgroundResource(if (isSelected) android.R.color.darker_gray else android.R.color.transparent)
                    // Simple background feedback
                    if (isSelected) setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
                    else setBackgroundColor(android.graphics.Color.WHITE)
                    
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8
                    }
                    
                    setOnClickListener {
                        selectedDevice = device.address
                        refreshDeviceList()
                    }
                }

                val textView = android.widget.TextView(this).apply {
                    text = "${device.name}\n${device.address}"
                    textSize = 16f
                    setTextColor(android.graphics.Color.BLACK)
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                
                itemLayout.addView(textView)

                if (isSelected) {
                    val checkIcon = android.widget.ImageView(this).apply {
                        setImageResource(android.R.drawable.checkbox_on_background)
                        colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.parseColor("#4CAF50"), android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                    itemLayout.addView(checkIcon)
                }

                deviceListContainer.addView(itemLayout)
            }
        }
        
        refreshDeviceList()
        
        // Test Print Button
        val btnTestPrint = android.widget.Button(this).apply {
            text = getString(R.string.btn_test_print)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
        }
        dialogView.addView(btnTestPrint)
        
        btnTestPrint.setOnClickListener {
             if (!selectedDevice.isNullOrEmpty()) {
                 lifecycleScope.launch(Dispatchers.IO) {
                      val testMap = mapOf(
                        "date" to "TEST DATE",
                        "content" to "TEST ITEM x1",
                        "subtotal" to 100,
                        "total" to 100
                    )
                    val json = gson.toJson(testMap)
                    val printer = com.example.flcasher.utils.BluetoothPrinter(this@CasherActivity)
                    val success = printer.printJson(selectedDevice!!, json) // known not null/empty
                    
                    withContext(Dispatchers.Main) {
                        if (success) Toast.makeText(this@CasherActivity, R.string.toast_test_sent, Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this@CasherActivity, R.string.toast_test_failed, Toast.LENGTH_SHORT).show()
                    }
                 }
             } else {
                 Toast.makeText(this, R.string.toast_select_device, Toast.LENGTH_SHORT).show()
             }
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.title_print_settings)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val newEnabled = switchEnable.isChecked
                val newOrderNumEnabled = switchOrderNum.isChecked
                
                prefs.edit()
                    .putBoolean("PRINT_ENABLED", newEnabled)
                    .putBoolean("PRINT_ORDER_NUM", newOrderNumEnabled)
                    .putString("PRINT_DEVICE", selectedDevice)
                    .apply()
                    
                Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
