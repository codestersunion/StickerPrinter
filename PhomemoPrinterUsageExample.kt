package com.phomemo.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Example usage of PhomemoPrinter with Android Bluetooth
 * 
 * This example shows how to:
 * 1. Connect to a Phomemo printer via Bluetooth
 * 2. Print an image from a file path or Bitmap
 */
class PhomemoPrinterUsageExample {
    
    // Standard Serial Port Profile UUID for RFCOMM
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    /**
     * Connect to a Phomemo printer and print an image
     * 
     * @param deviceAddress Bluetooth MAC address (e.g., "00:AA:13:41:11:A5")
     * @param imagePath Path to the image file
     * @param brightness Optional brightness adjustment (default 1.0)
     */
    suspend fun printImageFromPath(
        deviceAddress: String,
        imagePath: String,
        brightness: Float = 1.0f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                return@withContext Result.failure(IOException("Failed to decode image from path: $imagePath"))
            }
            printImage(deviceAddress, bitmap, brightness)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Connect to a Phomemo printer and print a Bitmap
     * 
     * @param deviceAddress Bluetooth MAC address (e.g., "00:AA:13:41:11:A5")
     * @param bitmap The bitmap to print
     * @param brightness Optional brightness adjustment (default 1.0)
     */
    suspend fun printImage(
        deviceAddress: String,
        bitmap: Bitmap,
        brightness: Float = 1.0f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var printer: PhomemoPrinter? = null
        
        try {
            // Get Bluetooth adapter
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                return@withContext Result.failure(IOException("Bluetooth adapter not available"))
            }
            
            if (!bluetoothAdapter.isEnabled) {
                return@withContext Result.failure(IOException("Bluetooth is not enabled"))
            }
            
            // Get the device
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            
            // Create socket and connect
            // Note: The channel parameter from Python is not needed in Android
            // Android uses the UUID to find the correct service
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            
            // Create printer instance with the socket's output stream
            printer = PhomemoPrinter(socket.outputStream)
            
            // Print the image
            printer.printImage(bitmap, brightness)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Clean up
            printer?.close()
            try {
                socket?.close()
            } catch (e: IOException) {
                // Ignore errors on close
            }
        }
    }
    
    /**
     * Example: Print an image from resources
     */
    suspend fun printImageFromResources(
        deviceAddress: String,
        context: android.content.Context,
        resourceId: Int,
        brightness: Float = 1.0f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            if (bitmap == null) {
                return@withContext Result.failure(IOException("Failed to decode image from resource: $resourceId"))
            }
            printImage(deviceAddress, bitmap, brightness)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Usage example in an Activity or Fragment:
 * 
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private val printerExample = PhomemoPrinterUsageExample()
 *     
 *     private fun printImage() {
 *         lifecycleScope.launch {
 *             val result = printerExample.printImage(
 *                 deviceAddress = "00:AA:13:41:11:A5",
 *                 bitmap = myBitmap,
 *                 brightness = 1.2f  // Increase brightness by 20%
 *             )
 *             
 *             result.onSuccess {
 *                 Toast.makeText(this@MainActivity, "Print successful!", Toast.LENGTH_SHORT).show()
 *             }.onFailure { error ->
 *                 Toast.makeText(this@MainActivity, "Print failed: ${error.message}", Toast.LENGTH_LONG).show()
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * Don't forget to add Bluetooth permissions to AndroidManifest.xml:
 * ```xml
 * <uses-permission android:name="android.permission.BLUETOOTH" />
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 * <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> <!-- For Android 12+ -->
 * ```
 */

