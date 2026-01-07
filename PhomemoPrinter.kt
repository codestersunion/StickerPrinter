package com.phomemo.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.IOException

/**
 * Phomemo Printer - Kotlin Android version
 * Based on the Python ESCPOS_printer.py implementation
 * Only implements image printing functionality
 */
class PhomemoPrinter(private val bluetoothSocket: java.io.OutputStream) {
    
    companion object {
        // ESC/POS Constants
        private const val ESC: Byte = 0x1B
        private const val GS: Byte = 0x1D
        
        // Image width constants
        private const val IMAGE_WIDTH_BYTES = 70
        private const val IMAGE_WIDTH_BITS = IMAGE_WIDTH_BYTES * 8 // 560 bits = 384 dots
        
        // ESC @ - initialize printer
        private val INITIALIZE = byteArrayOf(ESC, 0x40)
        
        // ESC a 1 - center alignment
        private val JUSTIFY_CENTER = byteArrayOf(ESC, 0x61, 0x01)
        
        // Header: INITIALIZE + JUSTIFY_CENTER + 0x1f 0x11 0x02 0x04
        private val HEADER = byteArrayOf(
            ESC, 0x40,  // INITIALIZE
            ESC, 0x61, 0x01.toByte(),  // JUSTIFY_CENTER
            0x1F.toByte(), 0x11, 0x02, 0x04
        )
        
        // Footer: 0x1F 0x11 0x08 0x1F 0x11 0x0E 0x1F 0x11 0x07 0x1F 0x11 0x09
        private val FOOTER = byteArrayOf(
            0x1F.toByte(), 0x11, 0x08,
            0x1F.toByte(), 0x11, 0x0E,
            0x1F.toByte(), 0x11, 0x07,
            0x1F.toByte(), 0x11, 0x09
        )
        
        // ESC d 2 - Print feed 2 lines
        private val PRINT_FEED = byteArrayOf(ESC, 0x64, 0x02)
        
        // GS v 0 - Print raster image
        private val GSV0 = byteArrayOf(GS, 0x76, 0x30, 0x00)
    }
    
    /**
     * Print an image
     *
     * @param bitmap The bitmap image to print
     * @param brightness Optional brightness adjustment (default 1.0). 
     *                   Values < 1.0 decrease brightness, > 1.0 increase brightness
     * @throws IOException if writing to the output stream fails
     */
    @Throws(IOException::class)
    fun printImage(bitmap: Bitmap, brightness: Float = 1.0f) {
        var image = bitmap
        
        // Rotate if width > height
        if (image.width > image.height) {
            val matrix = Matrix()
            matrix.postRotate(90f)
            image = Bitmap.createBitmap(
                image, 0, 0, image.width, image.height, matrix, true
            )
        }
        
        // Resize to IMAGE_WIDTH_BITS (560 bits = 70 bytes * 8)
        val newHeight = (image.height * IMAGE_WIDTH_BITS / image.width.toFloat()).toInt()
        image = Bitmap.createScaledBitmap(image, IMAGE_WIDTH_BITS, newHeight, true)
        
        // Adjust brightness if needed
        if (brightness != 1.0f) {
            image = adjustBrightness(image, brightness)
        }
        
        // Convert to black & white (1-bit)
        image = convertToBlackAndWhite(image)
        
        // Send header
        bluetoothSocket.write(HEADER)
        
        // Process image in chunks of 256 lines
        var startIndex = 0
        while (startIndex < image.height) {
            val endIndex = if (image.height - 256 > startIndex) {
                startIndex + 256
            } else {
                image.height
            }
            val lineHeight = endIndex - startIndex
            
            // Create block marker: GSV0 + width_bytes + 0x00 + (line_height - 1) + 0x00
            val blockMarker = byteArrayOf(
                GS, 0x76, 0x30, 0x00,
                IMAGE_WIDTH_BYTES.toByte(),
                0x00,
                (lineHeight - 1).toByte(),
                0x00
            )
            bluetoothSocket.write(blockMarker)
            
            // Process each line
            for (lineIndex in 0 until lineHeight) {
                val imageLine = ByteArray(IMAGE_WIDTH_BYTES)
                
                for (byteStart in 0 until IMAGE_WIDTH_BYTES) {
                    var byte: Byte = 0
                    for (bit in 0 until 8) {
                        val x = byteStart * 8 + bit
                        val y = lineIndex + startIndex
                        
                        if (x < image.width && y < image.height) {
                            val pixel = image.getPixel(x, y)
                            // In 1-bit mode, black pixels are 0, white are 1
                            // We need to invert: if pixel is black (0), set bit to 1
                            if (pixel == android.graphics.Color.BLACK) {
                                byte = (byte.toInt() or (1 shl (7 - bit))).toByte()
                            }
                        }
                    }
                    
                    // 0x0a breaks the rendering (it's processed as LineFeed by the printer)
                    if (byte == 0x0A.toByte()) {
                        byte = 0x14
                    }
                    
                    imageLine[byteStart] = byte
                }
                
                bluetoothSocket.write(imageLine)
            }
            
            startIndex = endIndex
        }
        
        // Send print feed (twice) and footer
        bluetoothSocket.write(PRINT_FEED)
        bluetoothSocket.write(PRINT_FEED)
        bluetoothSocket.write(FOOTER)
        bluetoothSocket.flush()
    }
    
    /**
     * Adjust the brightness of a bitmap
     *
     * @param bitmap The source bitmap
     * @param brightness Brightness multiplier (1.0 = no change, < 1.0 = darker, > 1.0 = brighter)
     * @return A new bitmap with adjusted brightness
     */
    private fun adjustBrightness(bitmap: Bitmap, brightness: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    /**
     * Convert a bitmap to black and white (1-bit)
     *
     * @param bitmap The source bitmap
     * @return A new 1-bit black and white bitmap
     */
    private fun convertToBlackAndWhite(bitmap: Bitmap): Bitmap {
        // Convert to 1-bit (black and white only) using threshold
        val bwBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Convert to grayscale using standard luminance formula
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            
            // Threshold: values below 128 become black, above become white
            pixels[i] = if (gray < 128) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
        
        bwBitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return bwBitmap
    }
    
    /**
     * Close the connection
     */
    fun close() {
        try {
            bluetoothSocket.close()
        } catch (e: IOException) {
            // Ignore errors on close
        }
    }
}

