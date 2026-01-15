package com.example.flcasher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val totalAmount: Int,
    var status: String = "PENDING", // PENDING, COMPLETED, CANCELLED
    val randomId: String? = null,
    val displayId: Int = 0, // For daily sequential numbering
    val isTakeout: Boolean = false
)
