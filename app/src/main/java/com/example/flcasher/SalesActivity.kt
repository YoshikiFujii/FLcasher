package com.example.flcasher

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flcasher.data.AppDatabase
import com.example.flcasher.data.model.Order
import com.example.flcasher.databinding.ActivitySalesBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val sdfDate = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()
        setupListeners()
        
        // Default load Today
        loadSalesForToday()
    }

    private fun setupListeners() {
        binding.btnFilterToday.setOnClickListener { loadSalesForToday() }
        binding.btnFilterWeek.setOnClickListener { loadSalesForWeek() }
        binding.btnPickDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        android.app.DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            loadSalesForDate(selectedYear, selectedMonth, selectedDay)
        }, year, month, day).show()
    }

    private fun loadSalesForDate(year: Int, month: Int, day: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, 0, 0, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis
        
        val displayDate = Calendar.getInstance()
        displayDate.set(year, month, day)
        binding.tvCurrentRange.text = sdfDate.format(displayDate.time)
        
        fetchAndDisplayData(startOfDay, endOfDay, isHourly = true)
    }

    private fun setupChart() {
        binding.chartSales.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            axisRight.isEnabled = false
            
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            
            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            
            legend.isEnabled = false
            animateY(1000)
        }
    }

    private fun loadSalesForToday() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis
        
        binding.tvCurrentRange.text = sdfDate.format(Date())
        
        fetchAndDisplayData(startOfDay, endOfDay, isHourly = true)
    }

    private fun loadSalesForWeek() {
        val calendar = Calendar.getInstance()
        // Reset to today to get end time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val endOfWeek = calendar.timeInMillis
        
        // Go back 7 days
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val startOfWeek = calendar.timeInMillis

        binding.tvCurrentRange.text = "${sdfDate.format(Date(startOfWeek))} - ${sdfDate.format(Date(endOfWeek))}"
        
        fetchAndDisplayData(startOfWeek, endOfWeek, isHourly = false)
    }

    private fun fetchAndDisplayData(start: Long, end: Long, isHourly: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val orders = db.orderDao().getCompletedOrdersBetween(start, end)
            
            val totalSales = orders.sumOf { it.totalAmount }
            val count = orders.size

            // Aggregation for Chart
            val entries = if (isHourly) {
                aggregateHourly(orders)
            } else {
                aggregateDaily(orders)
            }

            withContext(Dispatchers.Main) {
                binding.tvTotalSales.text = "¥$totalSales"
                binding.tvOrderCount.text = "$count"
                
                updateChart(entries, isHourly)
            }
        }
    }

    private fun aggregateHourly(orders: List<Order>): Pair<List<BarEntry>, List<String>> {
        // 0-23 hours
        val salesByHour = FloatArray(24)
        orders.forEach {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            salesByHour[hour] += it.totalAmount.toFloat()
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        // Only show hours with data or ranges? Let's show specific ranges like 9am-10pm or just all
        // Showing all 24 might be crowded. Let's just map 0..23
        for (i in 0..23) {
            entries.add(BarEntry(i.toFloat(), salesByHour[i]))
            labels.add("$i:00") // simple label
        }
        return entries to labels
    }

    private fun aggregateDaily(orders: List<Order>): Pair<List<BarEntry>, List<String>> {
        // Map of "MM/dd" -> Total
        val salesMap = LinkedHashMap<String, Float>()
        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        
        // Pre-fill last 7 days keys? Or just iterate orders
        // Better to pre-fill range to show empty days
        // Re-calculate start range purely for labels?
        // Simpler: Just group orders by date
        
        // Let's create a map relative to TODAY-6 to TODAY
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        
        for (i in 0..6) {
            val key = sdf.format(cal.time)
            salesMap[key] = 0f
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        orders.forEach {
             val key = sdf.format(Date(it.timestamp))
             // If key falls in range (it should)
             if (salesMap.containsKey(key)) {
                 salesMap[key] = salesMap[key]!! + it.totalAmount.toFloat()
             }
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        var index = 0f
        salesMap.forEach { (date, amount) ->
            entries.add(BarEntry(index, amount))
            labels.add(date)
            index++
        }
        return entries to labels
    }

    private fun updateChart(data: Pair<List<BarEntry>, List<String>>, isHourly: Boolean) {
        val (entries, labels) = data
        val dataSet = BarDataSet(entries, "Sales")
        dataSet.color = Color.parseColor(if (isHourly) "#66BB6A" else "#42A5F5")
        dataSet.valueTextSize = 10f

        val barData = BarData(dataSet)
        binding.chartSales.data = barData
        binding.chartSales.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.chartSales.invalidate() // Refresh
    }
}
