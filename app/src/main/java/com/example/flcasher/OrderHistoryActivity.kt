package com.example.flcasher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flcasher.data.AppDatabase
import com.example.flcasher.databinding.ActivityOrderHistoryBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OrderHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderHistoryBinding
    private lateinit var adapter: OrderHistoryAdapter
    private val db by lazy { AppDatabase.getDatabase(this) }

    private val calendar = java.util.Calendar.getInstance()
    private val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupDateControls()
        loadOrdersForDate()
    }

    private fun setupRecyclerView() {
        adapter = OrderHistoryAdapter { orderItem ->
            showDeleteConfirmation(orderItem)
        }
        binding.rvOrderHistory.layoutManager = LinearLayoutManager(this)
        binding.rvOrderHistory.adapter = adapter
    }

    private fun setupDateControls() {
        updateDateText()

        binding.btnPrevDate.setOnClickListener {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            updateDateText()
            loadOrdersForDate()
        }

        binding.btnNextDate.setOnClickListener {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            updateDateText()
            loadOrdersForDate()
        }
        
        binding.tvCurrentDate.setOnClickListener {
            showDatePicker()
        }
    }
    
    private fun showDatePicker() {
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                updateDateText()
                loadOrdersForDate()
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun updateDateText() {
        binding.tvCurrentDate.text = dateFormatter.format(calendar.time)
    }

    private fun loadOrdersForDate() {
        // Start of day
        val startCal = calendar.clone() as java.util.Calendar
        startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        startCal.set(java.util.Calendar.MINUTE, 0)
        startCal.set(java.util.Calendar.SECOND, 0)
        startCal.set(java.util.Calendar.MILLISECOND, 0)
        
        // End of day
        val endCal = calendar.clone() as java.util.Calendar
        endCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        endCal.set(java.util.Calendar.MINUTE, 59)
        endCal.set(java.util.Calendar.SECOND, 59)
        endCal.set(java.util.Calendar.MILLISECOND, 999)

        // Observe
        // Remove previous observers if any? LiveData auto-manages lifecycle, 
        // but creating new observer potentially duplicates if we don't clear?
        // Actually `getAllOrders()` returns a NEW LiveData object each time if we act this way?
        // Room returns the same LiveData instance if query is same? No.
        
        // Better: store current LiveData and remove observer before observing new one.
        // Or simpler: Just observe. Since `this` is lifecycle owner, old observers are removed when activity destroyed?
        // No, if we call this multiple times, we get multiple observers valid for this lifecycle!
        // We must manage the observer manually or use Transformations / ViewModel.
        
        // For simplicity without ViewModel refactor:
        // Let's use `removeObservers` on the LiveData... but we don't have reference to previous LiveData easily unless we store it.
        
        // Let's implement a simple tracking.
        refreshOrders(startCal.timeInMillis, endCal.timeInMillis)
    }
    
    private var currentLiveData: androidx.lifecycle.LiveData<List<com.example.flcasher.data.model.OrderWithItems>>? = null
    
    private fun refreshOrders(start: Long, end: Long) {
        currentLiveData?.removeObservers(this)
        
        currentLiveData = db.orderDao().getOrdersByDateRange(start, end)
        currentLiveData?.observe(this, Observer { orders ->
            adapter.submitList(orders)
        })
    }
    
    private fun showDeleteConfirmation(item: com.example.flcasher.data.model.OrderWithItems) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Order")
            .setMessage("Are you sure you want to delete Order #${item.order.id}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteOrder(item.order.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteOrder(orderId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.orderDao().deleteOrderFull(orderId)
            // UI will auto-update via LiveData
        }
    }
}
