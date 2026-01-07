package com.codestersunion.stickerprinter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import com.google.genai.Client
import com.google.genai.types.GenerateContentResponse
import java.io.File
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ... existing imports ...

object ColoringBookGenerator {
    private const val TAG = "ColoringBookGenerator"
    
    suspend fun transcribeAudio(
        apiKey: String,
        audioFile: File
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isEmpty() || apiKey == "YOUR_GOOGLE_API_KEY_HERE") {
                    Log.e(TAG, "Invalid API key")
                    return@withContext null
                }

                val client = Client.builder()
                    .apiKey(apiKey)
                    .build()

                val audioBytes = audioFile.readBytes()
                // No need to base64 encode for Gemini SDK multimodal input, we use raw bytes or ByteString
                
                // Use Gemini 1.5 Flash for fast multimodal understanding
                val textPart = Part.builder().text("Transcribe this audio exactly as spoken.").build()
                val audioPart = Part.builder().inlineData(
                    com.google.genai.types.Blob.builder()
                        .mimeType("audio/mp4")
                        .data(audioBytes) // Directly pass ByteArray
                        .build()
                ).build()

                val content = Content.builder()
                    .parts(listOf(textPart, audioPart))
                    .build()

                val response = client.models.generateContent(
                    "gemini-2.0-flash-lite",
                    content, // Pass single content object, not list
                    null
                )

                // Extract text from response
                var transcript: String? = null
                val candidates = response.candidates()
                if (candidates.isPresent && candidates.get().isNotEmpty()) {
                    val candidate = candidates.get()[0]
                    val contentRes = candidate.content()
                    if (contentRes.isPresent) {
                        val partsRes = contentRes.get().parts()
                        if (partsRes.isPresent) {
                            for (part in partsRes.get()) {
                                if (part.text().isPresent) {
                                    transcript = part.text().get()
                                    break
                                }
                            }
                        }
                    }
                }
                
                return@withContext transcript
            } catch (e: Exception) {
                Log.e(TAG, "Audio transcription failed", e)
                return@withContext null
            }
        }
    }

    /**
     * Generates a coloring book page from a prompt using Gemini (Nano Banana).
     * Enforces a strict 600x800 output.
     * 
     * @param apiKey The Google API key from BuildConfig.GOOGLE_API_KEY
     * @param userPrompt The user's prompt describing what to draw (e.g., "a happy dinosaur")
     * @return A Bitmap of exactly 600x800 pixels, or null if generation fails
     */
    suspend fun generateColoringPage(
        apiKey: String,
        userPrompt: String
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isEmpty() || apiKey == "YOUR_GOOGLE_API_KEY_HERE") {
                    Log.e(TAG, "Invalid API key. Please set google_api_key in local.properties")
                    return@withContext null
                }
                
                // 1. Setup the Client
                val client = Client.builder()
                    .apiKey(apiKey)
                    .build()

                // 2. Engineer the prompt for Coloring Book Style
                // We force the style instructions here so the user doesn't have to.
                val fullPrompt = """
                    Create a black and white outline drawing for a kids coloring book.
                    Subject: $userPrompt.
                    Style: Clean lines, no shading, no grayscale, pure white background, simple distinct shapes.
                    High contrast line art.
                    Aspect ratio: 3:4 (portrait orientation).
                """.trimIndent()

                Log.d(TAG, "Generating coloring book page with prompt: $fullPrompt")

                // 3. Make the Request
                val response = try {
                    client.models.generateContent("gemini-2.5-flash-image", fullPrompt, null)
                } catch (e: Exception) {
                    Log.w(TAG, "gemini-2.5-flash-image failed, trying gemini-1.5-pro", e)
                    try {
                        client.models.generateContent("gemini-1.5-pro", fullPrompt, null)
                    } catch (e2: Exception) {
                        Log.e(TAG, "gemini-1.5-pro also failed", e2)
                        throw e2
                    }
                }

                Log.d(TAG, "Received response from Gemini API")
                Log.d(TAG, "Raw response: $response")

                // 4. Extract image from response
                // The response structure varies by model, but typically contains:
                // - candidates[0].content.parts[0].inlineData.data (for image generation models)
                // - or text response with base64 embedded
                val originalBitmap = extractImageFromResponse(response)
                
                if (originalBitmap == null) {
                    Log.e(TAG, "No image data found in response or failed to decode")
                    return@withContext null
                }

                Log.d(TAG, "Original image size: ${originalBitmap.width}x${originalBitmap.height}")

                // 5. Resize to exactly 600x800
                return@withContext resizeAndCropTo600x800(originalBitmap)

            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Network error: Unable to resolve host. Check internet connectivity.", e)
                e.printStackTrace()
                return@withContext null
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Network error: Connection timeout. Check internet connectivity.", e)
                e.printStackTrace()
                return@withContext null
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network error: ${e.message}", e)
                e.printStackTrace()
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Error generating coloring book page: ${e.message}", e)
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    /**
     * Resizes and crops any Bitmap to exactly 600x800 pixels using center crop strategy.
     * This prevents stretching/distortion by scaling to fill and trimming excess edges.
     */
    fun resizeAndCropTo600x800(source: Bitmap): Bitmap {
        val targetWidth = 600
        val targetHeight = 800
        
        // Create a new bitmap with the exact target dimensions
        val targetBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        
        // Fill with white background (good for coloring books)
        canvas.drawColor(Color.WHITE)
        
        // Calculate scaling to fill the destination (Center Crop)
        val scale: Float
        val dx: Float
        val dy: Float
        
        if (source.width * targetHeight > targetWidth * source.height) {
            // Source is wider relative to target - scale by height
            scale = targetHeight.toFloat() / source.height.toFloat()
            dx = (targetWidth - source.width * scale) * 0.5f
            dy = 0f
        } else {
            // Source is taller relative to target - scale by width
            scale = targetWidth.toFloat() / source.width.toFloat()
            dx = 0f
            dy = (targetHeight - source.height * scale) * 0.5f
        }
        
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        
        return targetBitmap
    }

    /**
     * Decodes a Base64 string to a Bitmap.
     */
    private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            // Remove data URL prefix if present (e.g., "data:image/png;base64,")
            val base64Data = if (base64Str.contains(",")) {
                base64Str.substringAfter(",")
            } else {
                base64Str
            }
            
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding base64 to bitmap", e)
            null
        }
    }

    /**
     * Extracts image data from the Gemini API response and returns it as a Bitmap.
     * Handles different response structures depending on the model used.
     */
    private fun extractImageFromResponse(response: GenerateContentResponse): Bitmap? {
        try {
            // Check if we have candidates
            val candidates = response.candidates()
            if (candidates.isPresent && candidates.get().isNotEmpty()) {
                val candidate = candidates.get()[0]
                val content = candidate.content()
                
                if (content.isPresent) {
                    val parts = content.get().parts()
                    if (parts.isPresent) {
                        for (part in parts.get()) {
                            // Check for inlineData
                            val inlineData = part.inlineData()
                            if (inlineData.isPresent) {
                                val data = inlineData.get().data()
                                if (data.isPresent) {
                                    val rawData = data.get()
                                    
                                    // Handle direct byte arrays
                                    if (rawData is ByteArray) {
                                        return BitmapFactory.decodeByteArray(rawData, 0, rawData.size)
                                    }
                                    
                                    // Handle Protobuf ByteString
                                    if (rawData is ByteString) {
                                        val bytes = rawData.toByteArray()
                                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    }
                                    
                                    // Handle ByteBuffer
                                    if (rawData is java.nio.ByteBuffer) {
                                        val bytes = ByteArray(rawData.remaining())
                                        rawData.get(bytes)
                                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    }
                                    
                                    // Handle String (Base64)
                                    val strData = rawData as? String
                                    if (strData != null && strData.isNotEmpty()) {
                                        return decodeBase64ToBitmap(strData)
                                    }
                                    
                                    Log.w(TAG, "Unknown data type in inlineData: ${rawData.javaClass.name}")
                                }
                            }
                            
                            // Check text content for base64 data
                            val text = part.text()
                            if (text.isPresent) {
                                val textValue = text.get()
                                if (textValue.isNotEmpty()) {
                                    // Look for base64 patterns in the text
                                    val base64Pattern = Regex("data:image/[^;]+;base64,([A-Za-z0-9+/=]+)")
                                    val match = base64Pattern.find(textValue)
                                    if (match != null) {
                                        return decodeBase64ToBitmap(match.groupValues[1])
                                    }
                                    
                                    // If entire text looks like base64, try to decode it
                                    if (textValue.matches(Regex("^[A-Za-z0-9+/=\\s]+$"))) {
                                        return decodeBase64ToBitmap(textValue.replace("\\s".toRegex(), ""))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Fallback: try convenience method if available
            val responseText = response.text()
            if (responseText != null && responseText.isNotEmpty()) {
                // Look for base64 patterns in the text
                val base64Pattern = Regex("data:image/[^;]+;base64,([A-Za-z0-9+/=]+)")
                val match = base64Pattern.find(responseText)
                if (match != null) {
                    return decodeBase64ToBitmap(match.groupValues[1])
                }
                
                // If entire text looks like base64, try to decode it
                if (responseText.matches(Regex("^[A-Za-z0-9+/=\\s]+$"))) {
                    return decodeBase64ToBitmap(responseText.replace("\\s".toRegex(), ""))
                }
            }
            
            Log.w(TAG, "Could not extract image data from response structure")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting image from response", e)
            e.printStackTrace()
            return null
        }
    }
}
