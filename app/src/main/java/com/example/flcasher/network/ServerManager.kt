package com.example.flcasher.network

import android.content.Context
import com.example.flcasher.data.AppDatabase
import com.example.flcasher.data.model.Order
import com.example.flcasher.data.model.OrderItem
import com.google.gson.Gson
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.request.receiveText
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap



class ServerManager(private val context: Context) {
    private var server: NettyApplicationEngine? = null
    private val db = AppDatabase.getDatabase(context)
    private val gson = Gson()
    
    // Kitchen Subscribers
    private val kitchenSessions = Collections.newSetFromMap(ConcurrentHashMap<WebSocketSession, Boolean>())
    
    private val currentDisplayId = java.util.concurrent.atomic.AtomicInteger(0)

    fun startServer() {
        if (server != null) return

        server = embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                gson {}
            }
            install(WebSockets) {
                pingPeriod = java.time.Duration.ofSeconds(15)
                timeout = java.time.Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                // Return Product List
                get("/products") {
                    val products = db.productDao().getAllProductsSync()
                    call.respond(products)
                }

                // Add Product
                post("/products") {
                    try {
                        val content = call.receiveText()
                        val product = gson.fromJson(content, com.example.flcasher.data.model.Product::class.java)
                        db.productDao().insertProduct(product)
                        call.respondText("Product Added")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respondText("Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                    }
                }

                // Delete Product
                delete("/products/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id != null) {
                        val product = db.productDao().getProductById(id)
                        if (product != null) {
                            db.productDao().deleteProduct(product)
                            call.respondText("Product Deleted")
                        } else {
                            call.respond(io.ktor.http.HttpStatusCode.NotFound)
                        }
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    }
                }

                post("/order") {
                    try {
                        val content = call.receiveText()
                        val request = gson.fromJson(content, OrderRequest::class.java)
                        
                        // Increment Daily Order Number
                        val displayId = currentDisplayId.incrementAndGet()
                        
                        val order = com.example.flcasher.data.model.Order(
                            timestamp = request.timestamp,
                            totalAmount = request.totalAmount,
                            status = request.status,
                            randomId = request.randomId,
                            displayId = displayId
                        )
                        
                        // Transactional insert helper in DAO or manually
                        val id = db.orderDao().insertOrder(order)
                        
                        val orderItems = request.items.map { item ->
                            com.example.flcasher.data.model.OrderItem(
                                orderId = id,
                                productId = item.productId,
                                productName = item.productName,
                                quantity = item.quantity,
                                priceAtSale = item.priceAtSale
                            )
                        }
                        db.orderDao().insertOrderItems(orderItems)
                        
                        notifyKitchen(id)
                        
                        val responseMap = mapOf("id" to id, "displayId" to displayId)
                        call.respond(responseMap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respondText("Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                    }
                }
                
                // Reset Daily Order Number
                post("/reset-order-number") {
                    currentDisplayId.set(0)
                    call.respondText("Order Number Reset")
                }
                
                // WebSocket for Kitchen
                webSocket("/kitchen") {
                    kitchenSessions.add(this)
                    try {
                        for (frame in incoming) {
                            // Listen for messages if needed
                        }
                    } finally {
                        kitchenSessions.remove(this)
                    }
                }
                
                // Get Active Orders for Kitchen
                get("/kitchen/orders") {
                    val orders = db.orderDao().getOrdersByStatusSync("PENDING")
                    call.respond(orders)
                }
                
                // Get All Orders (History)
                get("/kitchen/history") {
                    val orders = db.orderDao().getAllOrdersSync()
                    call.respond(orders)
                }
                
                // Complete Order
                post("/order/{id}/complete") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id != null) {
                        db.orderDao().updateOrderStatus(id, "COMPLETED")
                        notifyKitchen(id, "ORDER_COMPLETED") 
                        call.respondText("Order Completed")
                    } else {
                        call.respondText("Invalid ID", status = io.ktor.http.HttpStatusCode.BadRequest)
                    }
                }
                
                // Revert Order (Back to Pending)
                post("/order/{id}/revert") {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id != null) {
                        db.orderDao().updateOrderStatus(id, "PENDING")
                        notifyKitchen(id, "ORDER_REVERTED") 
                        call.respondText("Order Reverted")
                    } else {
                        call.respondText("Invalid ID", status = io.ktor.http.HttpStatusCode.BadRequest)
                    }
                }
                
                // Serve Images
                get("/images/{name}") {
                    val name = call.parameters["name"]
                    if (name != null) {
                        val file = java.io.File(this@ServerManager.context.filesDir, name)
                        if (file.exists()) {
                            call.respondFile(file)
                        } else {
                            call.respond(io.ktor.http.HttpStatusCode.NotFound)
                        }
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    }
                }
                
                // Get Total Sales Since X
                get("/sales/total") {
                    val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                    val total = db.orderDao().getSumOfOrdersSince(since) ?: 0
                    call.respondText(total.toString())
                }
            }
        }.start(wait = false)
    }

    fun stopServer() {
        server?.stop(1000, 2000)
        server = null
    }

    // Call this when DB updates
    fun notifyKitchen(orderId: Long, type: String = "NEW_ORDER") {
        val message = """{"type":"$type", "orderId": $orderId}"""
        CoroutineScope(Dispatchers.IO).launch {
            kitchenSessions.forEach { session ->
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
