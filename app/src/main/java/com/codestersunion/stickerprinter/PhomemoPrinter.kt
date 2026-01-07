package com.codestersunion.stickerprinter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Phomemo Printer - Kotlin Android version
 * Uses TSPL (Thermal Printer Language) protocol.
 * Implements "Stop-and-Wait" Flow Control for Bluetooth reliability.
 */
class PhomemoPrinter(
    private val outputStream: OutputStream,
    private val inputStream: InputStream? = null,
    private val labelWidthMm: Int = DEFAULT_LABEL_WIDTH_MM,
    private val labelHeightMm: Int = DEFAULT_LABEL_HEIGHT_MM
) {

    companion object {
        private const val TAG = "Phomemo"
        private val CRLF = "\r\n".toByteArray(StandardCharsets.US_ASCII)

        private const val DEFAULT_LABEL_WIDTH_MM = 102
        private const val DEFAULT_LABEL_HEIGHT_MM = 152
        private val FRAME_DELIMITER = 0x7E.toByte()
    }

    /**
     * PRIVATE HELPER: Handles the "Chunk -> Send -> Wait for ACK" loop
     * Matches original parameters.
     */
    private fun sendPacketized(data: ByteArray, input: InputStream) {
        var offset = 0
        val totalBytes = data.size
        val MAX_RETRIES = 3
        val MAX_PAYLOAD_SIZE = 256 // CRITICAL: Hardware buffer size

        Log.d("Phomemo", "Starting Packetized Send. Total bytes: $totalBytes")

        while (offset < totalBytes) {
            // 1. Create Chunk
            val length = kotlin.math.min(MAX_PAYLOAD_SIZE, totalBytes - offset)
            val chunk = ByteArray(length)
            System.arraycopy(data, offset, chunk, 0, length)

            var retryCount = 0
            var chunkSuccess = false

            while (retryCount < MAX_RETRIES && !chunkSuccess) {
                try {
                    // 2. Build Protocol Frame
                    // Format: 7E + Type(01) + LenLow + LenHigh + Payload + Checksum + 7E
                    val frameStream = ByteArrayOutputStream()

                    // A. Start & Type
                    frameStream.write(0x7E)
                    frameStream.write(0x01)

                    // B. Length (Little Endian)
                    // Example: 256 -> Low: 00, High: 01
                    frameStream.write(length and 0xFF)         // Low Byte
                    frameStream.write((length shr 8) and 0xFF) // High Byte

                    // C. Payload
                    frameStream.write(chunk)

                    // D. Calculate Checksum (Sum of Payload XOR 0x80)
                    // Note: The hardware specifically calculates this on the PAYLOAD ONLY.
                    var sum = 0
                    for (b in chunk) {
                        sum += (b.toInt() and 0xFF)
                    }
                    val checksum = (sum xor 0x80).toByte()
                    frameStream.write(checksum.toInt())

                    // E. Footer
                    frameStream.write(0x7E)

                    // 3. Write to Socket
                    val packet = frameStream.toByteArray()
                    logPacket("PhomemoRaw", "TX Chunk >>", packet) // Log for debugging
                    outputStream.write(packet)
                    outputStream.flush()

                    // 4. WAIT for 3-Byte ACK (7E 01 7E)
                    // Assuming waitForThreeByteAck consumes input until it finds the token
                    if (waitForThreeByteAck(input)) {
                        chunkSuccess = true
                        offset += length
                        // Log.d("Phomemo", "Chunk sent. Progress: $offset / $totalBytes")
                    } else {
                        Log.w("Phomemo", "NACK or Timeout at offset $offset. Retrying...")
                        throw IOException("NACK received")
                    }

                } catch (e: Exception) {
                    retryCount++
                    Log.e("Phomemo", "Retry $retryCount/$MAX_RETRIES at offset $offset: ${e.message}")
                    if (retryCount >= MAX_RETRIES) {
                        throw IOException("Failed to send packet at offset $offset after $MAX_RETRIES retries")
                    }
                    SystemClock.sleep(200)
                    // Clear input buffer before retry
                    while (input.available() > 0) input.read()
                }
            }
        }
        Log.d("Phomemo", "Packetized Send Complete.")
    }

    /**
     * Helper to ensure we get exactly 7E 01 7E.
     * Returns true if ACK found, false on NACK (7E 00 7E) or Timeout.
     */
    private fun waitForThreeByteAck(input: InputStream): Boolean {
        val startTime = System.currentTimeMillis()
        val buffer = ByteArray(64) // Small buffer for ACKs

        while (System.currentTimeMillis() - startTime < 3000) { // 3s Timeout
            if (input.available() >= 3) {
                val len = input.read(buffer)
                if (len > 0) {
                    logPacket("PhomemoRaw", "RX ACK <<", buffer.copyOf(len))

                    // Scan for 7E 01 7E
                    for (i in 0 until len - 2) {
                        if (buffer[i] == 0x7E.toByte() && buffer[i+2] == 0x7E.toByte()) {
                            if (buffer[i+1] == 0x01.toByte()) return true // ACK
                            if (buffer[i+1] == 0x00.toByte()) return false // NACK
                        }
                    }
                }
            }
            SystemClock.sleep(10)
        }
        return false // Timeout
    }

    @Throws(IOException::class)
    fun testConnection(dots: Int = 50): Boolean {
        try {
            val feedCommand = "FEED $dots$CRLF"
            outputStream.write(feedCommand.toByteArray(StandardCharsets.US_ASCII))
            outputStream.flush()
            return true
        } catch (e: IOException) {
            throw e
        }
    }

    @Throws(IOException::class)
    fun requestStatus(timeoutMs: Long = 1500): Byte? {
        val input = inputStream ?: throw IOException("Printer input stream not available for status requests")
        val command = "!S$CRLF"
        Log.d("Phomemo", "Requesting printer status...")
        outputStream.write(command.toByteArray(StandardCharsets.US_ASCII))
        outputStream.flush()

        val startTime = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - startTime < timeoutMs) {
            if (input.available() > 0) {
                val value = input.read()
                if (value >= 0) {
                    val statusByte = value.toByte()
                    val hexString = "%02X".format(value)
                    Log.d("Phomemo", "Printer status response: 0x$hexString (decimal: $value)")
                    return statusByte
                }
            } else {
                SystemClock.sleep(50)
            }
        }
        Log.w("Phomemo", "Status request timed out after ${timeoutMs}ms")
        return null
    }

    fun debugHandshake() {
        val input = inputStream ?: return
        val output = outputStream

        Log.d("PhomemoDebug", "=== STARTING RAW HANDSHAKE DEBUG ===")

        // Define Packets
        val step1_SN = hexStringToByteArray("7E010A00535353474554534E0D0A6B7E")
        val step2_Bulk = hexStringToByteArray("7E013100535353474554424D41504D4F44450D0A53535347455456455253494F4E0D0A5353534745544341500D0A42545645520D0A087E")
        val step3_Paper = hexStringToByteArray("7E010D0053535347455450415045520D0A277E")
        val step4_Unlock = step1_SN // Same as step 1

        // Helper to read whatever is currently there
        fun readAndLog(tag: String) {
            SystemClock.sleep(100) // Give a tiny window for bytes to arrive
            if (input.available() > 0) {
                val buffer = ByteArray(input.available())
                input.read(buffer)
                logPacket("PhomemoDebug", "$tag RX <<", buffer)
            } else {
                Log.d("PhomemoDebug", "$tag RX << (No Data)")
            }
        }

        // --- STEP 1: GET SN ---
        output.write(step1_SN); output.flush()
        Log.d("PhomemoDebug", "Step 1 TX >> SSSGETSN")
        SystemClock.sleep(200)
        readAndLog("Step 1")

        // --- STEP 2: BULK REQUEST ---
        output.write(step2_Bulk); output.flush()
        Log.d("PhomemoDebug", "Step 2 TX >> SSSGETBMAPMODE...")

        // CRITICAL TEST:
        // In trace, we get ~01~ (ACK) immediately, then we MUST send Step 3.
        // Let's see if we get that 7E 01 7E here.
        SystemClock.sleep(50)
        readAndLog("Step 2 (Immediate ACK?)")

        // --- STEP 3: PAPER REQUEST ---
        // In trace, this happens BEFORE the bulk data fully arrives
        output.write(step3_Paper); output.flush()
        Log.d("PhomemoDebug", "Step 3 TX >> SSSGETPAPER (Sent early per trace)")

        // Now we wait a bit longer to catch the Bulk Data AND the Paper response
        SystemClock.sleep(500)
        readAndLog("Step 2 Data + Step 3 ACK")

        // --- STEP 4: UNLOCK ---
        output.write(step4_Unlock); output.flush()
        Log.d("PhomemoDebug", "Step 4 TX >> SSSGETSN (Unlock)")
        SystemClock.sleep(200)
        readAndLog("Step 4")
    }

    /**
     * Verified Handshake Sequence (5 steps).
     * Returns a status string on success, throws IOException on failure.
     */
    @Throws(IOException::class)
    fun forceConnection(): String {
        val input = inputStream ?: throw IOException("Printer input stream not available")
        val output = outputStream

        Log.d(TAG, "=== Starting Handshake (Protocol Verified) ===")

        // 1. CLEAR BUFFER
        if (input.available() > 0) {
            val dumped = ByteArray(input.available())
            input.read(dumped)
            logPacket("PhomemoRaw", "CLEAR BUFFER >>", dumped)
        }

        // --- Define Packets ---
        val handshakeSn = byteArrayOf(0x7E.toByte(), 0x01, 0x0A, 0x00) +
                "SSSGETSN".toByteArray(StandardCharsets.US_ASCII) +
                "\r\n".toByteArray(StandardCharsets.US_ASCII) +
                byteArrayOf(0x6B.toByte(), 0x7E.toByte())

        val capabilitiesPayload = "SSSGETBMAPMODE\r\nSSSGETVERSION\r\nSSSGETCAP\r\nBTVER\r\n"
        val handshakeCaps = byteArrayOf(0x7E.toByte(), 0x01, 0x31, 0x00) +
                capabilitiesPayload.toByteArray(StandardCharsets.US_ASCII) +
                byteArrayOf(0x08, 0x7E.toByte())

        val handshakePaper = byteArrayOf(0x7E.toByte(), 0x01, 0x0D, 0x00) +
                "SSSGETPAPER".toByteArray(StandardCharsets.US_ASCII) +
                "\r\n".toByteArray(StandardCharsets.US_ASCII) +
                byteArrayOf(0x27.toByte(), 0x7E.toByte())

        // --- Execute Steps ---

        // Step 1: GET SN
        if (!runHandshakeStep(input, output, "Handshake 1/5 (SN)", handshakeSn, "SSSGETSN:")) {
            throw IOException("Handshake Step 1 Failed")
        }

        // Step 2: BULK REQUEST
        if (!runHandshakeStep(input, output, "Handshake 2/5 (Bulk)", handshakeCaps, "SSSGETBMAPMODE")) {
            throw IOException("Handshake Step 2 Failed")
        }

        // Step 3: PAPER CHECK
        // We capture the response here to extract paper status for the return string
        val paperResponseBytes = runHandshakeStepWithReturn(input, output, "Handshake 3/5 (Paper)", handshakePaper, "SSSGETPAPER:")
            ?: throw IOException("Handshake Step 3 Failed")

        // Clean up the response to just get the value (e.g., "SSSGETPAPER:YES" -> "YES")
        val paperStringFull = String(paperResponseBytes, StandardCharsets.US_ASCII)
        // Simple extraction logic: Find "SSSGETPAPER:" and take the rest of the line
        val paperStatus = paperStringFull.substringAfter("SSSGETPAPER:").trim().takeWhile { !it.isWhitespace() }

        // Step 4: UNLOCK (REPEAT SN) - The critical final ping
        if (!runHandshakeStep(input, output, "Handshake 4/5 (Unlock)", handshakeSn, "SSSGETSN:")) {
            throw IOException("Handshake Step 4 Failed")
        }

        Log.d(TAG, "✅ Handshake Complete. Printer Unlocked.")
        return "Connection Verified. Paper: $paperStatus"
    }

    /**
     * Helper: Sends packet, waits for ACK + Data, logs via logPacket, and validates.
     * Returns true if successful.
     */
    private fun runHandshakeStep(
        input: InputStream,
        output: OutputStream,
        stepName: String,
        packet: ByteArray,
        expectedContent: String
    ): Boolean {
        return runHandshakeStepWithReturn(input, output, stepName, packet, expectedContent) != null
    }

    /**
     * Helper: Same as above but returns the raw response bytes on success.
     */
    private fun runHandshakeStepWithReturn(
        input: InputStream,
        output: OutputStream,
        stepName: String,
        packet: ByteArray,
        expectedContent: String
    ): ByteArray? {
        try {
            // 1. Log & Write TX
            logPacket("PhomemoRaw", "$stepName TX >>", packet)
            output.write(packet)
            output.flush()

            // 2. Wait for data
            var attempts = 0
            while (input.available() == 0 && attempts < 20) {
                SystemClock.sleep(50)
                attempts++
            }

            if (input.available() == 0) {
                Log.e("PhomemoRaw", "❌ $stepName Failed: Timeout (No Response)")
                return null
            }

            // 3. Read Everything Available
            val buffer = ByteArray(input.available())
            val len = input.read(buffer)

            // 4. Log RX
            logPacket("PhomemoRaw", "$stepName RX <<", buffer)

            // 5. Validation Logic

            // A. Check for Protocol ACK Header (7E 01 7E)
            if (len < 3 || buffer[0] != 0x7E.toByte() || buffer[1] != 0x01.toByte() || buffer[2] != 0x7E.toByte()) {
                Log.e("PhomemoRaw", "❌ $stepName Failed: Missing ACK header (7E 01 7E).")
                return null
            }

            // B. Check for Expected String Content
            val responseStr = String(buffer, 0, len, StandardCharsets.US_ASCII)
            if (!responseStr.contains(expectedContent)) {
                Log.e("PhomemoRaw", "❌ $stepName Failed: Missing expected content '$expectedContent'.")
                return null
            }

            return buffer

        } catch (e: Exception) {
            Log.e("PhomemoRaw", "❌ $stepName Exception: ${e.message}")
            return null
        }
    }

    fun testPrintText() {
        val setup = "SIZE 100 mm,150 mm\r\nGAP 3 mm,0 mm\r\nCLS\r\n"
        outputStream.write(setup.toByteArray(StandardCharsets.US_ASCII))
        val textCmd = "TEXT 100,100,\"3\",0,2,2,\"ANDROID TEST\"\r\n"
        outputStream.write(textCmd.toByteArray(StandardCharsets.US_ASCII))
        outputStream.write("PRINT 1,1\r\n".toByteArray(StandardCharsets.US_ASCII))
        outputStream.flush()
    }

    /**
     * HEX DUMP LOGGER
     * Prints data like Wireshark: "7E 01 00 ... | ~..OK"
     */
    private fun logPacket(tag: String, prefix: String, data: ByteArray) {
        val hex = StringBuilder()
        val ascii = StringBuilder()

        for (b in data) {
            // Convert to Hex (e.g., "7E ")
            hex.append(String.format("%02X ", b))

            // Convert to ASCII (replace non-printables with dot)
            val c = b.toInt().toChar()
            if (c >= ' ' && c <= '~') {
                ascii.append(c)
            } else {
                ascii.append('.')
            }
        }
        Log.d(tag, "$prefix [${data.size}]: $hex \n $ascii")
    }

    fun close() {
        try {
            outputStream.close()
            inputStream?.close()
        } catch (e: IOException) {
            // Ignore
        }
    }

    /**
     * THROWAWAY TEST: Sends the exact hex string captured from Labelife.
     * If this works, the hardware and connection are 100% fine.
     */
    fun testExactLabelifePacket() {

        forceConnection()
        val input = inputStream ?: return

        // 1. Clear buffer
        while (input.available() > 0) input.read()

        // 2. The RAW Packet (Cleaned of HCI headers, starts at 7E, ends at 7E)
        val hexString =
            "7E010001" +
                    "0D0A44454E534954592031310D0A" + // DENSITY 11..
                    "535045454420340D0A5245464552454E" + // SPEED 4..REFEREN
                    "434520302C300D0A4F46465345542030" + // CE 0,0..OFFSET 0
                    "206D6D0D0A53495A4520313032206D6D" + //  mm..SIZE 102 mm
                    "2C313532206D6D0D0A47415020332E30" + // ,152 mm..GAP 3.0
                    "30206D6D2C302E3030206D6D0D0A4449" + // 0 mm,0.00 mm..DI
                    "52454354494F4E20302C300D0A434C53" + // RECTION 0,0..CLS
                    "0D0A4249544D415020302C302C313032" + // ..BITMAP 0,0,102
                    "2C313231392C342C4F03000002FFFFFF" + // ,1219,4,O.......
                    "FFFF2000000010110000203D01000FB4" + // .. ....... =....
                    "71203D910100D50C1FB00D3014003760" + // q =........0..7`
                    "0030A8003945008FAD033FB800301400" + // .0..9E....?..0..
                    "20259501CFA00B20000002950187BC44" + //  %..... .......D
                    "2014140020088C090FFE01FC1F83C180" + //  ... ...........
                    "1C3FC007F0FFF0FC00F001200AD80120" + // .?......... ...
                    "08940103FC00FC1F0381740C0403F0FF" + // ..........t.....
                    "E07C" + // Payload End
                    "857E" // Checksum (85) + Footer (7E) + Extra (86)

        val packet = hexStringToByteArray(hexString)

        Log.d("PhomemoRaw", "TEST >> Sending Labelife Replay [${packet.size} bytes]")
        logPacket("PhomemoRaw", "TEST >>", packet)

        outputStream.write(packet)
        outputStream.flush()

        // 3. Wait for response
        SystemClock.sleep(200)
        val buffer = ByteArray(1024)
        if (input.available() > 0) {
            val len = input.read(buffer)
            val response = buffer.copyOfRange(0, len)
            logPacket("PhomemoRaw", "TEST <<", response)

            if (response.size >= 3 && response[1] == 0x01.toByte()) {
                Log.d("PhomemoRaw", "✅ SUCCESS! Printer accepted the packet.")
            } else {
                Log.e("PhomemoRaw", "❌ FAILURE! Printer returned Error/NACK.")
            }
        } else {
            Log.e("PhomemoRaw", "❌ TIMEOUT! No response from printer.")
        }
    }

    // Helper function
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * THE FINAL SOLUTION: Prints an image using Raw TSPL Mode 0.
     * Requires forceConnection() to be called first.
     */
    fun printBitmap(bitmap: Bitmap) {
        val output = outputStream
        val input = inputStream ?: return // We need input to check for responses

        try {
            // Log input bitmap properties
            Log.d(TAG, "=== PRINT BITMAP START ===")
            Log.d(TAG, "Input Bitmap: ${bitmap.width}x${bitmap.height}, format=${bitmap.config}, hasAlpha=${bitmap.hasAlpha()}")
            Log.d(TAG, "Input Bitmap size: ${bitmap.byteCount} bytes (${bitmap.byteCount / 1024} KB)")

            // 1. Scale & Convert (Your logic was correct, 0=Black based on JS)
            // Target width 800 ensures 32-bit alignment (safe).
            val targetWidth = 800
            Log.d(TAG, "Scaling bitmap to target width: $targetWidth pixels")
            val scaledBitmap = scaleBitmapToWidth(bitmap, targetWidth)
            Log.d(TAG, "Scaled Bitmap: ${scaledBitmap.width}x${scaledBitmap.height}, format=${scaledBitmap.config}")
            
            Log.d(TAG, "Converting bitmap to mono image...")
            val monoImage = convertForColoringPage(scaledBitmap)
            Log.d(TAG, "Mono Image: ${monoImage.widthBytes} bytes wide, ${monoImage.height} lines")
            Log.d(TAG, "Mono Image data size: ${monoImage.data.size} bytes (${monoImage.data.size / 1024} KB)")

            Log.d(TAG, "Generating Print Job (${monoImage.widthBytes} bytes wide, ${monoImage.height} lines)...")
            val jobStream = ByteArrayOutputStream()

            // 2. SETUP COMMANDS
            // SIZE is 102mm (Paper Size), not image size.
            val heightMm = (monoImage.height / 8) + 10 // Add padding
            Log.d(TAG, "Calculated paper height: $heightMm mm (image height: ${monoImage.height} lines)")

            val header = StringBuilder()
                .append("DENSITY 11\r\n")
                .append("SPEED 4\r\n")
                .append("SIZE 102 mm,$heightMm mm\r\n")
                .append("GAP 3 mm,0 mm\r\n")
                .append("DIRECTION 0,0\r\n")
                .append("CLS\r\n")
                .append("BITMAP 0,0,${monoImage.widthBytes},${monoImage.height},0,")
                .toString()
                .toByteArray(StandardCharsets.US_ASCII)

            Log.d(TAG, "Header size: ${header.size} bytes")
            Log.d(TAG, "Header content: ${String(header, StandardCharsets.US_ASCII)}")
            jobStream.write(header)
            
            Log.d(TAG, "Writing mono image data (${monoImage.data.size} bytes)...")
            jobStream.write(monoImage.data)

            // 3. PRINT TRIGGER
            val footer = "\r\nPRINT 1\r\n".toByteArray()
            Log.d(TAG, "Footer size: ${footer.size} bytes")
            jobStream.write(footer)

            // 4. TRANSMISSION (Chunked & Paced)
            val fullData = jobStream.toByteArray()
            val totalBytes = fullData.size
            var offset = 0
            // Use 4KB chunks - standard max buffer for many BT chips
            val chunkSize = 4096

            Log.d(TAG, "TX >> Starting Transmission of $totalBytes bytes (${totalBytes / 1024} KB) in ${(totalBytes + chunkSize - 1) / chunkSize} chunks...")
            Log.d(TAG, "Chunk size: $chunkSize bytes, sleep between chunks: 20ms")

            var chunkNumber = 0
            while (offset < totalBytes) {
                val len = Math.min(chunkSize, totalBytes - offset)
                chunkNumber++
                val progressPercent = (offset * 100) / totalBytes
                Log.d(TAG, "TX >> Chunk $chunkNumber: sending $len bytes (offset: $offset, progress: $progressPercent%)")
                
                try {
                    output.write(fullData, offset, len)
                    output.flush()
                    Log.d(TAG, "TX >> Chunk $chunkNumber: flushed successfully")
                } catch (e: IOException) {
                    Log.e(TAG, "TX >> Chunk $chunkNumber: FAILED to write/flush at offset $offset", e)
                    throw e
                }

                offset += len

                // CRITICAL: Small sleep to let the Bluetooth stack drain
                // 60KB without sleep overflows the internal buffer on many phones/printers
                SystemClock.sleep(20)
            }

            Log.d(TAG, "TX >> All $chunkNumber chunks sent. Total bytes transmitted: $offset/$totalBytes")
            Log.d(TAG, "TX >> Data flushed. Waiting for physical transmission...")

            // 5. CRITICAL WAIT (Prevent Socket Close Race)
            // Wait for the printer to potentially reply or just finish receiving.
            // If we close now, the last ~10KB might still be in the air.
            val waitStart = System.currentTimeMillis()
            val buffer = ByteArray(128)
            var responseCount = 0
            while (System.currentTimeMillis() - waitStart < 3000) { // Wait up to 3 seconds
                if (input.available() > 0) {
                    val readLen = input.read(buffer)
                    val resp = String(buffer, 0, readLen)
                    responseCount++
                    Log.d(TAG, "RX << Response #$responseCount (${readLen} bytes): $resp")
                    // If we get "Cmd error", we know immediately something is wrong
                    if (resp.contains("error", ignoreCase = true)) {
                        Log.e(TAG, "RX << ERROR DETECTED in printer response!")
                        break
                    }
                }
                SystemClock.sleep(100)
            }
            
            val waitDuration = System.currentTimeMillis() - waitStart
            if (responseCount == 0) {
                Log.w(TAG, "RX << No response from printer after ${waitDuration}ms")
            } else {
                Log.d(TAG, "RX << Received $responseCount response(s) from printer")
            }

            Log.d(TAG, "✅ Job Complete. Socket safe to close.")
            Log.d(TAG, "=== PRINT BITMAP END ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Print Failed", e)
            Log.e(TAG, "Print Failed - Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // Data Class to hold conversion result
    data class MonoImage(val widthBytes: Int, val height: Int, val data: ByteArray)

    /**
     * Converts Bitmap to Printer-Ready Bytes.
     * Logic: 0 = Black (Ink), 1 = White (Paper).
     * Enforces 4-Byte Alignment for safety.
     */
    fun convertForColoringPage(inputBitmap: Bitmap): MonoImage {
        Log.d(TAG, "convertForColoringPage: Starting conversion")
        Log.d(TAG, "convertForColoringPage: Input bitmap ${inputBitmap.width}x${inputBitmap.height}")
        
        // 1. TARGET WIDTH CALCULATION (Critical for Line Quality)
        // The PM-241BT is 203 DPI at 4.25 inches = ~864 dots max width.
        // We target 800px to be safe and perfectly aligned.
        // If we don't resize here, the printer will drop lines internally.
        val targetWidth = 800
        val aspectRatio = inputBitmap.height.toFloat() / inputBitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        Log.d(TAG, "convertForColoringPage: Target dimensions ${targetWidth}x${targetHeight} (aspect ratio: $aspectRatio)")

        // 2. SCALE & PRE-PROCESS
        // We redraw the bitmap to the correct size BEFORE converting bytes.
        val scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, targetWidth, targetHeight, true)
        Log.d(TAG, "convertForColoringPage: Scaled bitmap ${scaledBitmap.width}x${scaledBitmap.height}")

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        // 3. ALIGNMENT (4-Byte Boundary)
        val rawWidthBytes = (width + 7) / 8
        val alignedWidthBytes = if (rawWidthBytes % 4 == 0) {
            rawWidthBytes
        } else {
            rawWidthBytes + (4 - (rawWidthBytes % 4))
        }
        Log.d(TAG, "convertForColoringPage: Width bytes - raw: $rawWidthBytes, aligned: $alignedWidthBytes")

        // 4. CONVERSION LOOP
        val data = ByteArray(alignedWidthBytes * height) { 0xFF.toByte() }
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        Log.d(TAG, "convertForColoringPage: Processing ${width * height} pixels, threshold: 200 (luma < 200 = black)")

        var blackPixelCount = 0
        var whitePixelCount = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Luminance
                val luma = (r * 0.299 + g * 0.587 + b * 0.114).toInt()

                // *** THE FIX ***
                // Old Threshold: 128 (50% Gray)
                // New Threshold: 200 (Light Gray)
                // This means anything darker than a very light pencil mark becomes Solid Black.
                // This effectively "Bolds" the lines.
                if (luma < 200) {
                    blackPixelCount++
                    val byteIndex = y * alignedWidthBytes + (x / 8)
                    val bitIndex = 7 - (x % 8)

                    // Set bit to 0 (Black)
                    data[byteIndex] = (data[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
                } else {
                    whitePixelCount++
                }
            }
        }
        
        val totalPixels = width * height
        val blackPercent = (blackPixelCount * 100) / totalPixels
        Log.d(TAG, "convertForColoringPage: Conversion complete - black pixels: $blackPixelCount ($blackPercent%), white pixels: $whitePixelCount")
        Log.d(TAG, "convertForColoringPage: Output data size: ${data.size} bytes (${data.size / 1024} KB)")

        return MonoImage(alignedWidthBytes, height, data)
    }

    /**
     * Simple scaler to ensure width fits the page.
     */
    private fun scaleBitmapToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) {
            Log.d(TAG, "scaleBitmapToWidth: No scaling needed (already $targetWidth pixels wide)")
            return bitmap
        }
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        Log.d(TAG, "scaleBitmapToWidth: Scaling from ${bitmap.width}x${bitmap.height} to ${targetWidth}x${targetHeight} (aspect: $aspectRatio)")
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
