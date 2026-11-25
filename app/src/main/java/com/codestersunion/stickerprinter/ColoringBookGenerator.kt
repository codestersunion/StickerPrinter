package com.codestersunion.stickerprinter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thrown when the Gemini API returns a non-2xx response (bad key, billing, quota, etc.)
 * so callers can tell a service problem apart from "nothing was heard".
 */
class GeminiApiException(val statusCode: Int, val apiStatus: String?, message: String) :
    Exception("HTTP $statusCode${apiStatus?.let { " $it" } ?: ""}: $message")

/** What the AI understood from a child's request. */
data class StickerIdea(
    /** Cleaned-up, kid-safe description of what to draw. */
    val subject: String,
    /** Short, friendly sentence shown to the child (used when the request wasn't understood). */
    val reply: String,
    /** False when the speech was noise or made no sense. */
    val understood: Boolean,
    /** Optional short name, e.g. "Skateboard Dragon". */
    val title: String? = null
)

object ColoringBookGenerator {
    private const val TAG = "ColoringBookGenerator"
    private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val TRANSCRIPTION_MODEL = "gemini-3.5-transcribe"
    private const val COLORING_PAGE_MODEL = "gemini-3.1-flash-image"
    /** Fast, cheap text model used to understand what the child asked for. */
    private const val UNDERSTANDING_MODEL = "gemini-3.5-flash-lite"

    private fun isValidKey(apiKey: String) =
        apiKey.isNotEmpty() && apiKey != "YOUR_GOOGLE_API_KEY_HERE"

    /**
     * Transcribes recorded audio (AAC in MP4, from [AudioRecorder]) via Gemini generateContent.
     * Returns null when nothing usable was transcribed; throws [GeminiApiException] on API errors.
     */
    suspend fun transcribeAudio(
        apiKey: String,
        audioFile: File
    ): String? = withContext(Dispatchers.IO) {
        if (!isValidKey(apiKey)) {
            Log.e(TAG, "Invalid API key. Please set google_api_key in local.properties")
            return@withContext null
        }

        val audioBase64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject()
                                        .put("mimeType", "audio/mp4")
                                        .put("data", audioBase64)
                                )
                            )
                            .put(
                                JSONObject().put(
                                    "text",
                                    "Transcribe this audio exactly as spoken. " +
                                        "Respond with only the transcription, or an empty response if there is no speech."
                                )
                            )
                    )
                )
            )

        val responseBody = postGenerateContent(apiKey, TRANSCRIPTION_MODEL, requestBody)
        val text = extractTextFromGenerateContentResponse(responseBody).trim()
        Log.d(TAG, "Transcription: \"$text\"")
        text.ifEmpty { null }
    }

    /**
     * Turns a child's (often rambling, mumbled or half-finished) words into a clear,
     * kid-safe drawing subject plus a short friendly sentence to show the child.
     *
     * Never throws: if the understanding model is unavailable we fall back to the raw
     * transcript so a misconfigured model never blocks a sticker.
     */
    suspend fun interpretRequest(apiKey: String, transcript: String): StickerIdea =
        withContext(Dispatchers.IO) {
            val fallback = StickerIdea(
                subject = transcript,
                reply = "Ooh, ${transcript.trimEnd('.', '!', '?')}! Let me draw it!",
                understood = true
            )
            if (!isValidKey(apiKey)) return@withContext fallback

            val instructions = """
                You help young children (ages 3-10) make black-and-white stickers by talking.
                You get a speech transcript of what a child said. It may include filler words
                ("um", "can I have", "I want"), repeats, baby talk, mispronunciations or
                transcription mistakes. Figure out what the child most likely wants a picture of.

                Rules:
                - "subject": a short, vivid description of the picture to draw (max 25 words),
                  keeping every detail the child asked for (characters, actions, numbers, names).
                - If the child asked for words or a name on the sticker, include the exact
                  spelling in quotes in the subject.
                - If they say "anything", "surprise me", "I don't know", or similar, invent a fun,
                  whimsical subject a child would love.
                - If the request is scary, violent, gory, or not suitable for young kids, gently
                  turn it into a friendly, silly version (e.g. "a monster eating people" ->
                  "a goofy friendly monster eating a giant sandwich"). Never lecture.
                - If the transcript is empty, just noise, or makes no sense at all, set
                  "understood" to false.
                - "reply": one short, excited sentence (max 12 words) for the child,
                  using very simple words, e.g. "Ooh, a dragon on a skateboard! Let me draw it!"
                  If not understood: "Hmm, I didn't catch that. Can you say it again?"
                - "title": 1-4 word name for the sticker, e.g. "Skateboard Dragon".
            """.trimIndent()

            val requestBody = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions))))
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Transcript: \"$transcript\"")))))
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put(
                            "responseSchema",
                            JSONObject()
                                .put("type", "OBJECT")
                                .put(
                                    "properties",
                                    JSONObject()
                                        .put("subject", JSONObject().put("type", "STRING"))
                                        .put("title", JSONObject().put("type", "STRING"))
                                        .put("reply", JSONObject().put("type", "STRING"))
                                        .put("understood", JSONObject().put("type", "BOOLEAN"))
                                )
                                .put("required", JSONArray().put("subject").put("reply").put("understood"))
                        )
                )

            try {
                val json = JSONObject(
                    extractTextFromGenerateContentResponse(postGenerateContent(apiKey, UNDERSTANDING_MODEL, requestBody))
                )
                StickerIdea(
                    subject = json.optString("subject").ifBlank { transcript },
                    reply = json.optString("reply").ifBlank { fallback.reply },
                    understood = json.optBoolean("understood", true),
                    title = json.optString("title").ifBlank { null }
                ).also { Log.d(TAG, "Interpreted \"$transcript\" -> $it") }
            } catch (e: Exception) {
                Log.w(TAG, "Request interpretation failed, using raw transcript", e)
                fallback
            }
        }

    /** Art styles rotated between generations so stickers don't all look the same. */
    private val STYLES = listOf(
        "chunky cute kawaii cartoon with big round eyes and soft rounded shapes",
        "classic children's coloring book illustration",
        "bold comic-book style with dynamic pose",
        "playful hand-drawn doodle style with a few fun little details around it",
        "storybook illustration style, charming and whimsical",
        "simple, bold sticker-style mascot drawing"
    )

    /**
     * Generates a coloring book page from a prompt using the Gemini image model.
     * Enforces a strict 600x800 output. Retries once if no image comes back.
     * Returns null if no image came back; throws [GeminiApiException] on API errors.
     *
     * @param style optional art style; a random one is picked when null so "make another"
     *              gives a fresh take on the same idea.
     */
    suspend fun generateColoringPage(
        apiKey: String,
        userPrompt: String,
        style: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!isValidKey(apiKey)) {
            Log.e(TAG, "Invalid API key. Please set google_api_key in local.properties")
            return@withContext null
        }

        val chosenStyle = style ?: STYLES.random()
        // Tuned for a black & white thermal label printer: thin lines and gray disappear,
        // and empty margins waste the sticker.
        val fullPrompt = """
            Draw a sticker for a young child to print and color in.
            Subject: $userPrompt
            Art style: $chosenStyle.
            Requirements:
            - Pure black lines on a pure white background. No gray, no shading, no gradients, no textures.
            - Thick, bold, confident outlines (they must survive a low-resolution thermal printer).
            - Small solid black areas are OK for eyes, pupils and tiny details only.
            - Large, simple, clearly separated shapes that are easy to color.
            - The subject must be big and fill most of the portrait frame, with only a small margin.
            - Friendly, happy, expressive and appealing to kids aged 3-10. Nothing scary.
            - No text, letters or numbers unless the subject explicitly asks for words; if it does,
              spell them exactly as given in big, clear letters.
            - No border or frame around the image.
        """.trimIndent()

        Log.d(TAG, "Generating coloring book page with prompt: $fullPrompt")

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", fullPrompt))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("IMAGE"))
                    .put(
                        "imageConfig",
                        JSONObject()
                            .put("aspectRatio", "3:4")
                            .put("imageSize", "1K")
                    )
            )

        repeat(2) { attempt ->
            val responseBody = postGenerateContent(apiKey, COLORING_PAGE_MODEL, requestBody)
            val originalBitmap = extractBitmapFromGenerateContentResponse(responseBody)
            if (originalBitmap != null) {
                Log.d(TAG, "Original image size: ${originalBitmap.width}x${originalBitmap.height}")
                return@withContext resizeAndCropTo600x800(originalBitmap)
            }
            Log.e(TAG, "No image data found (attempt ${attempt + 1}): ${responseBody.take(500)}")
        }
        null
    }

    /** POSTs to `models/{model}:generateContent` and returns the body, or throws [GeminiApiException]. */
    private fun postGenerateContent(apiKey: String, model: String, requestBody: JSONObject): String {
        val connection = (URL("$API_BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.use { it.write(requestBody.toString().toByteArray(Charsets.UTF_8)) }

            val statusCode = connection.responseCode
            if (statusCode in 200..299) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            }

            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val error = runCatching { JSONObject(errorBody).optJSONObject("error") }.getOrNull()
            val exception = GeminiApiException(
                statusCode = statusCode,
                apiStatus = error?.optString("status")?.ifEmpty { null },
                message = error?.optString("message")?.ifEmpty { null } ?: errorBody.take(500)
            )
            Log.e(TAG, "$model generateContent failed: ${exception.message}")
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    private fun extractTextFromGenerateContentResponse(responseBody: String): String {
        val parts = JSONObject(responseBody)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: return ""

        return buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                // Skip thought summaries if the model includes them
                if (part.optBoolean("thought", false)) continue
                append(part.optString("text"))
            }
        }
    }

    private fun extractBitmapFromGenerateContentResponse(responseBody: String): Bitmap? {
        val candidates = JSONObject(responseBody).optJSONArray("candidates") ?: return null
        for (candidateIndex in 0 until candidates.length()) {
            val parts = candidates
                .optJSONObject(candidateIndex)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: continue

            for (partIndex in 0 until parts.length()) {
                val part = parts.optJSONObject(partIndex) ?: continue
                val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                val data = inlineData?.optString("data").orEmpty()
                if (data.isNotEmpty()) {
                    return decodeBase64ToBitmap(data)
                }

                if (part.has("text")) {
                    extractBitmapFromText(part.optString("text"))?.let { return it }
                }
            }
        }

        return null
    }

    /**
     * Extracts a Bitmap from text if the model returns base64.
     */
    private fun extractBitmapFromText(text: String?): Bitmap? {
        if (text == null || text.isEmpty()) return null

        return try {
            // Look for base64 patterns in the text (sometimes Gemini embeds them)
            val base64Pattern = Regex("data:image/[^;]+;base64,([A-Za-z0-9+/=]+)")
            val match = base64Pattern.find(text)
            if (match != null) {
                return decodeBase64ToBitmap(match.groupValues[1])
            }
            
            // If entire text looks like base64, try to decode it
            if (text.matches(Regex("^[A-Za-z0-9+/=\\s]+$"))) {
                return decodeBase64ToBitmap(text.replace("\\s".toRegex(), ""))
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resizes and crops any Bitmap to exactly 600x800 pixels using center crop strategy.
     */
    fun resizeAndCropTo600x800(source: Bitmap): Bitmap {
        val targetWidth = 600
        val targetHeight = 800

        val targetBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        canvas.drawColor(Color.WHITE)
        
        val scale: Float
        val dx: Float
        val dy: Float
        
        if (source.width * targetHeight > targetWidth * source.height) {
            scale = targetHeight.toFloat() / source.height.toFloat()
            dx = (targetWidth - source.width * scale) * 0.5f
            dy = 0f
        } else {
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

    private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
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
}
