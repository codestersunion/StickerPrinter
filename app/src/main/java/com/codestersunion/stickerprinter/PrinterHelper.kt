package com.codestersunion.stickerprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Helper class for connecting to Phomemo printers and printing images
 */
object PrinterHelper {
    private const val TAG = "PrinterHelper"

    // Standard Serial Port Profile UUID for RFCOMM
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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
            Log.d(TAG, "=== PRINT IMAGE START ===")
            Log.d(TAG, "Device address: $deviceAddress")
            Log.d(TAG, "Input bitmap: ${bitmap.width}x${bitmap.height}, format=${bitmap.config}, hasAlpha=${bitmap.hasAlpha()}, brightness=$brightness")
            
            // Get Bluetooth adapter
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth adapter not available")
                return@withContext Result.failure(IOException("Bluetooth adapter not available"))
            }

            if (!bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth is not enabled")
                return@withContext Result.failure(IOException("Bluetooth is not enabled"))
            }

            // Get the device
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            Log.d(TAG, "Connecting to device: ${device.name ?: "Unknown"} ($deviceAddress)")

            // Create socket and connect
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            Log.d(TAG, "Creating RFCOMM socket...")
            socket.connect()
            Log.d(TAG, "Socket connected successfully")

            // Create printer instance with the socket's output stream
            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )
            Log.d(TAG, "Printer instance created, calling printBitmap()...")

            // Print the image
//            printer.printImage(bitmap, brightness)
//            printer.testSafePrint()
            printer.printBitmap(
                bitmap = bitmap,
            )
            Log.d(TAG, "✅ Print job completed successfully")
            Log.d(TAG, "=== PRINT IMAGE END ===")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Print image failed", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        } finally {
            // Clean up
            Log.d(TAG, "Cleaning up resources...")
            printer?.close()
            try {
                socket?.close()
                Log.d(TAG, "Socket closed")
            } catch (e: IOException) {
                Log.w(TAG, "Error closing socket: ${e.message}")
                // Ignore errors on close
            }
        }
    }

    /**
     * Test printer connection by sending a feed command
     * This is a non-destructive test that advances the paper without printing
     *
     * @param deviceAddress Bluetooth MAC address (e.g., "00:AA:13:41:11:A5")
     * @param dots Number of dots to feed (default 50, approximately 1-2mm)
     * @return Result<Unit> - success if command was sent, failure with error message otherwise
     */
    suspend fun testConnection(
        deviceAddress: String,
        dots: Int = 50
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
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            // Create printer instance with the socket's output stream
            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )

            // Test connection by sending feed command
            printer.testConnection(dots)

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
     * Request the TSPL status byte from the printer using the !S command
     *
     * @param deviceAddress Bluetooth MAC address (e.g., "00:AA:13:41:11:A5")
     * @param timeoutMs Maximum time to wait for the response
     * @return Result containing the status byte (null if timed out)
     */
    suspend fun queryStatus(
        deviceAddress: String,
        timeoutMs: Long = 1500
    ): Result<Byte?> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var printer: PhomemoPrinter? = null

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                return@withContext Result.failure(IOException("Bluetooth adapter not available"))
            }

            if (!bluetoothAdapter.isEnabled) {
                return@withContext Result.failure(IOException("Bluetooth is not enabled"))
            }

            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )

            val status = printer.requestStatus(timeoutMs)
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            printer?.close()
            try {
                socket?.close()
            } catch (e: IOException) {
                // Ignore errors on close
            }
        }
    }

    /**
     * Force connection using the framed handshake protocol
     * Replicates the Python script's force_connection() function
     *
     * This connects to the printer and calls PhomemoPrinter.forceConnection()
     * to send a framed handshake (SSSGETSN) followed by configuration commands.
     *
     * @param deviceAddress Bluetooth MAC address (e.g., "00:AA:13:41:11:A5")
     * @return Result containing the handshake response message and success status
     */
    suspend fun forceConnection(
        deviceAddress: String
    ): Result<String> = withContext(Dispatchers.IO) {
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
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            // Create printer instance with both streams
            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )

            // Call the force connection method
            val response = printer.forceConnection()

            // Return success with response message
            if (response.isNotEmpty()) {
                Result.success("Handshake successful. Printer replied: $response")
            } else {
                Result.success("Handshake sent. No text reply (proceeding to blind print...)")
            }

        } catch (e: IOException) {
            val errorMessage = when {
                e.message?.contains("Resource busy", ignoreCase = true) == true -> {
                    "Resource Busy. Turn Bluetooth OFF and ON, and ensure no other app is using the printer."
                }
                e.message?.contains("Permission denied", ignoreCase = true) == true -> {
                    "Permission Denied. The port is stale. Un-pair and Re-pair the printer."
                }
                else -> e.message ?: "Connection failed: ${e.javaClass.simpleName}"
            }
            Result.failure(IOException(errorMessage, e))
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
     * Test print text on the printer
     * Sends a simple text print command to verify the printer is working
     *
     * @param deviceAddress Bluetooth MAC address (e.g., "00:AA:13:41:11:A5")
     * @return Result<Unit> - success if command was sent, failure with error message otherwise
     */
    suspend fun testPrintText(
        deviceAddress: String
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
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            // Create printer instance with the socket's output stream
            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )

            // Test print text
            printer.testPrintText()

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

    suspend fun testPacket(
        deviceAddress: String
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
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            // Create printer instance with the socket's output stream
            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )

            printer.testExactLabelifePacket()
            Result.success(Unit)
        }catch (e: Exception) {
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

    suspend fun debugHandshake(
        deviceAddress: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var printer: PhomemoPrinter? = null

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                return@withContext Result.failure(IOException("Bluetooth adapter not available"))
            }

            if (!bluetoothAdapter.isEnabled) {
                return@withContext Result.failure(IOException("Bluetooth is not enabled"))
            }

            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )

            printer.debugHandshake()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            printer?.close()
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }

    suspend fun printWizardSpiderHeroImage(
        deviceAddress: String,
        context: Context,
        resourceId: Int? = null,
        x: Int = 10,
        y: Int = 10
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var printer: PhomemoPrinter? = null

        try {
            // 1. Load Image
            var originalBitmap = if (resourceId != null) {
                BitmapFactory.decodeResource(context.resources, resourceId)
            } else {
                val resId = context.resources.getIdentifier("a_star", "drawable", context.packageName)
                if (resId != 0) BitmapFactory.decodeResource(context.resources, resId) else null
            }

//            originalBitmap = generateComplexTestBitmap(200, 200)
            if (originalBitmap == null) return@withContext Result.failure(IOException("Image not found"))

            // 2. SCALE & DIAGNOSTIC SETUP
            val maxPrinterWidth = 800 // 4 inches @ 203dpi

            // Scale the image
            val aspectRatio = originalBitmap.height.toFloat() / originalBitmap.width.toFloat()
            val newWidth = minOf(maxPrinterWidth, originalBitmap.width)
            val newHeight = (newWidth * aspectRatio).toInt()

            /**
             * Normalizes a resource/file bitmap for Thermal Printing.
             * 1. Flattens transparency onto a WHITE background.
             * 2. Ensures ARGB_8888 config (prevents HARDWARE bitmap crashes).
             * 3. Scales to the target width immediately to save memory.
             */
            fun loadAndNormalizeBitmap(original: Bitmap, targetWidth: Int = 800): Bitmap {
//                val options = BitmapFactory.Options().apply {
//                    inScaled = false
//                    // Optional: Pre-scale if the image is massive to avoid OOM
//                }

//                // 1. Decode Raw
//                val original = BitmapFactory.decodeResource(context.resources, resId, options)
//                    ?: throw IllegalArgumentException("Resource not found")

                // 2. Resize first (Optimization)
                // We scale scaling *before* flattening to save processing time on huge photos
                val scaled = if (original.width != targetWidth) {
                    val aspectRatio = original.height.toFloat() / original.width.toFloat()
                    val targetHeight = (targetWidth * aspectRatio).toInt()
                    Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
                } else {
                    original
                }

                // 3. Create a Safe Canvas (White Background)
                // Thermal printers need WHITE to mean "No Ink". Transparency (0,0,0,0) is mathmatically "Black".
                val normalized = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(normalized)

                // A. Fill with White (The Paper color)
                canvas.drawColor(Color.WHITE)

                // B. Draw the image on top
                canvas.drawBitmap(scaled, 0f, 0f, null)

                return normalized
            }

            // Create a mutable bitmap to draw our diagnostic lines
            val finalBitmap = loadAndNormalizeBitmap(originalBitmap)

            // DRAW DIAGNOSTIC BORDER (Crucial for debugging)
            // This proves if the command works. If you see a box but no wizard, it's a contrast issue.
//            val canvas = Canvas(finalBitmap)
//            val paint = Paint().apply {
//                color = Color.BLACK
//                style = Paint.Style.STROKE
//                strokeWidth = 4f
//            }
//            canvas.drawRect(0f, 0f, finalBitmap.width.toFloat(), finalBitmap.height.toFloat(), paint)

            // 3. Connect
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            printer = PhomemoPrinter(socket.outputStream, socket.inputStream)

            // 4. HANDSHAKE (Unlock the printer)
//            printer.forceConnection()

            // 5. PRINT with HIGH CONTRAST
            // We use a specialized threshold (200) to turn light grays into black
//            printer.printBitmapWithProtocol(finalBitmap, x, y)

            printer.printBitmap(finalBitmap)
//            printer.printBitmap(generateComplexTestBitmap())
//            printer.testTrojanHorse()

            Thread.sleep(3000)
            Result.success(Unit)

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            printer?.close()
            try { socket?.close() } catch (e: IOException) {}
        }
    }



    /**
     * Generates a complex test pattern to stress test the printer protocol.
     * Dimensions: 800x600 (Fits standard 4-inch label width)
     */
    fun generateComplexTestBitmap(width: Int = 800, height: Int = 600): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // 1. Fill Background White (Thermal printers need a white canvas to define "empty")
        canvas.drawColor(Color.WHITE)

        // 2. Draw Alignment Border (Black, 4px thick)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRect(2f, 2f, width - 2f, height - 2f, paint)

        // 3. Draw "X" through the middle (Checks for shearing/packet loss)
        paint.strokeWidth = 2f
        canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawLine(0f, height.toFloat(), width.toFloat(), 0f, paint)

        // 4. Draw Concentric Circles (Checks Aspect Ratio)
        paint.style = Paint.Style.STROKE
        val centerX = width / 2f
        val centerY = height / 2f
        for (r in 50..250 step 50) {
            canvas.drawCircle(centerX, centerY, r.toFloat(), paint)
        }

        // 5. Draw a "Heavy Density" Bar (Stresses the power supply/buffer)
        paint.style = Paint.Style.FILL
        canvas.drawRect(50f, height - 100f, width - 50f, height - 50f, paint)

        // 6. Draw Text Info (Checks resolution/Dithering)
        paint.textSize = 40f
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        // Draw text in White on top of the black bar
        paint.color = Color.WHITE
        canvas.drawText("PROTOCOL STRESS TEST", centerX, height - 65f, paint)

        // Draw text in Black on top
        paint.color = Color.BLACK
        canvas.drawText("800x600 px", centerX, 60f, paint)

        // 7. Draw a Checkerboard (Binary 101010 stress)
        // Small 20x20 pixel area of single-pixel dots
        paint.strokeWidth = 1f
        for (y in 100 until 200 step 4) {
            for (x in 50 until 150 step 4) {
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }

        return bitmap
    }

    /**
     * Test Safe LZO implementation
     * Runs the testsafe function which sends a simple black square using Safe Mode LZO
     *
     * @param deviceAddress Bluetooth MAC address (e.g., "00:AA:13:41:11:A5")
     * @return Result<Unit> - success if test was sent, failure with error message otherwise
     */
    suspend fun testSafeLzo(
        deviceAddress: String
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
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            // Create printer instance with the socket's output stream
            printer = PhomemoPrinter(
                outputStream = socket.outputStream,
                inputStream = socket.inputStream
            )

            // Run the safe test
            printer.printBitmap(generateComplexTestBitmap())

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
}

