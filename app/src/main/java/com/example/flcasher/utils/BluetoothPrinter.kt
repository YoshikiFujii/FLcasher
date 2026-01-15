package com.example.flcasher.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinter(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    // Matching ReceiveAndPrintActivity UUID
    private val uuid: UUID = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices ?: emptySet()
    }

    @SuppressLint("MissingPermission")
    suspend fun printJson(deviceAddress: String, json: String, retries: Int = 3): Boolean {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            while (attempt < retries) {
                attempt++
                var socket: BluetoothSocket? = null
                var outputStream: OutputStream? = null
                try {
                    // Debug log
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Printer: Connecting to $deviceAddress (Attempt $attempt)", Toast.LENGTH_SHORT).show() }

                    val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                    if (device == null) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Printer: Device not found", Toast.LENGTH_SHORT).show() }
                        return@withContext false
                    }

                    bluetoothAdapter?.cancelDiscovery()

                    try {
                        socket = device.createRfcommSocketToServiceRecord(uuid)
                        socket.connect()
                    } catch (e: Exception) {
                        // Fallback mechanism
                        try {
                            socket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
                            socket!!.connect()
                        } catch (e2: Exception) {
                            throw e
                        }
                    }

                    withContext(Dispatchers.Main) { Toast.makeText(context, "Printer: Connected!", Toast.LENGTH_SHORT).show() }

                    outputStream = socket!!.outputStream
                    outputStream!!.write(json.toByteArray(Charsets.UTF_8))
                    outputStream!!.flush()
                    
                    Thread.sleep(500)
                    return@withContext true

                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Printer Error: ${e.message}. Retrying...", Toast.LENGTH_SHORT).show() }
                    
                    // Cleanup before retry
                    try {
                        outputStream?.close()
                        socket?.close()
                    } catch (closeEx: Exception) {
                        closeEx.printStackTrace()
                    }
                    
                    if (attempt < retries) {
                        kotlinx.coroutines.delay(1000) // Wait 1 second before retry
                    }
                }
            }
            // Failed after all retries
            withContext(Dispatchers.Main) { Toast.makeText(context, "Printer Failed after $retries attempts", Toast.LENGTH_LONG).show() }
            return@withContext false
        }
    }
}
