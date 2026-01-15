package com.example.flcasher.network

data class OrderRequest(
    val timestamp: Long,
    val totalAmount: Int,
    val status: String,
    val items: List<OrderItemRequest>,
    val randomId: String? = null
)

data class OrderItemRequest(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val priceAtSale: Int
)
