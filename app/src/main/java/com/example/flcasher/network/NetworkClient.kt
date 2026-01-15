package com.example.flcasher.network

import com.example.flcasher.data.model.Product
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.gson.gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class NetworkClient {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson()
        }
        install(WebSockets)
    }
    private val gson = Gson()
    var serverIp: String = "192.168.1.1" // Will be updated by UI

    suspend fun getProducts(): List<Product> {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get("http://$serverIp:8080/products")
                val json = response.bodyAsText()
                val type = object : TypeToken<List<Product>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun addProduct(product: Product): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(product)
                val response = client.post("http://$serverIp:8080/products") {
                    contentType(ContentType.Application.Json)
                    setBody(json)
                }
                response.status.value in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun deleteProduct(id: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.delete("http://$serverIp:8080/products/$id")
                response.status.value in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun sendOrder(orderJson: String): OrderResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val response: io.ktor.client.statement.HttpResponse = client.post("http://$serverIp:8080/order") {
                    contentType(ContentType.Application.Json)
                    setBody(orderJson)
                }
                
                if (response.status.value in 200..299) {
                    val responseText = response.bodyAsText()
                    // Parse JSON
                    return@withContext gson.fromJson(responseText, OrderResponse::class.java)
                } else {
                    return@withContext null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    suspend fun getKitchenOrders(): List<com.example.flcasher.data.model.OrderWithItems> {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get("http://$serverIp:8080/kitchen/orders")
                val json = response.bodyAsText()
                val type = object : TypeToken<List<com.example.flcasher.data.model.OrderWithItems>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    suspend fun getKitchenHistory(): List<com.example.flcasher.data.model.OrderWithItems> {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get("http://$serverIp:8080/kitchen/history")
                val json = response.bodyAsText()
                val type = object : TypeToken<List<com.example.flcasher.data.model.OrderWithItems>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun completeOrder(orderId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Using POST for simplicity
                val response = client.post("http://$serverIp:8080/order/$orderId/complete")
                response.status.value in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun revertOrder(orderId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.post("http://$serverIp:8080/order/$orderId/revert")
                response.status.value in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun connectToKitchenWebSocket(): Flow<String> = flow {
        try {
            client.webSocket("ws://$serverIp:8080/kitchen") {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        emit(frame.readText())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun resetOrderNumber(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.post("http://$serverIp:8080/reset-order-number")
                response.status.value in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun getTotalSalesSince(timestamp: Long): Int {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get("http://$serverIp:8080/sales/total?since=$timestamp")
                response.bodyAsText().toIntOrNull() ?: 0
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }
}

data class OrderResponse(val id: Long, val displayId: Int)
