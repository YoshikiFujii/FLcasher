package com.example.flcasher.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.flcasher.data.model.Order
import com.example.flcasher.data.model.OrderItem
import com.example.flcasher.data.model.OrderWithItems

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): LiveData<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY timestamp ASC")
    fun getOrdersByStatus(status: String): LiveData<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY timestamp ASC")
    fun getOrdersByStatusSync(status: String): List<OrderWithItems>

    @Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrdersSync(): List<OrderWithItems>

    @Insert
    suspend fun insertOrder(order: Order): Long

    @Insert
    suspend fun insertOrderItems(items: List<OrderItem>)
    
    @Query("UPDATE orders SET status = :status WHERE id = :id")
    suspend fun updateOrderStatus(id: Long, status: String)

    @Transaction
    suspend fun insertFullOrder(order: Order, items: List<OrderItem>) {
        val orderId = insertOrder(order)
        val itemsWithId = items.map { it.copy(orderId = orderId) }
        insertOrderItems(itemsWithId)
    }

    @Query("SELECT * FROM orders WHERE timestamp BETWEEN :start AND :end AND status = 'COMPLETED' ORDER BY timestamp ASC")
    fun getCompletedOrdersBetween(start: Long, end: Long): List<Order>

    @Query("SELECT * FROM orders WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getOrdersByDateRange(start: Long, end: Long): LiveData<List<OrderWithItems>>

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)
    
    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: Long)
    
    @Transaction
    suspend fun deleteOrderFull(orderId: Long) {
        deleteOrderItems(orderId)
        deleteOrder(orderId)
    }
    
    @Query("SELECT SUM(totalAmount) FROM orders WHERE timestamp >= :since AND status != 'CANCELLED'")
    suspend fun getSumOfOrdersSince(since: Long): Int?
}
