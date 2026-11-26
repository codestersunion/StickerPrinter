package com.codestersunion.stickerprinter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A previously generated sticker saved on the device. */
data class SavedSticker(
    val id: String,
    val subject: String,
    val title: String?,
    val createdAt: Long,
    val file: File
) {
    fun loadBitmap(): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)
}

/**
 * Keeps the most recent [MAX_STICKERS] generated stickers in app-private storage so kids
 * can reprint a favourite without spending another AI generation.
 */
object StickerHistory {
    private const val TAG = "StickerHistory"
    const val MAX_STICKERS = 20
    private const val DIR_NAME = "sticker_history"
    private const val INDEX_FILE = "index.json"

    private val lock = Any()

    private fun dir(context: Context) = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Newest first. */
    suspend fun list(context: Context): List<SavedSticker> = withContext(Dispatchers.IO) {
        synchronized(lock) { readIndex(context) }
    }

    suspend fun save(context: Context, bitmap: Bitmap, subject: String, title: String?): SavedSticker? =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val now = System.currentTimeMillis()
                    val id = now.toString()
                    val file = File(dir(context), "$id.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                    val sticker = SavedSticker(id, subject, title, now, file)
                    val all = listOf(sticker) + readIndex(context)
                    val (keep, drop) = all.withIndex().partition { it.index < MAX_STICKERS }
                    drop.forEach { it.value.file.delete() }
                    writeIndex(context, keep.map { it.value })
                    sticker
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save sticker", e)
                    null
                }
            }
        }

    private fun readIndex(context: Context): List<SavedSticker> {
        val indexFile = File(dir(context), INDEX_FILE)
        if (!indexFile.exists()) return emptyList()
        return try {
            val array = JSONArray(indexFile.readText())
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id")
                val file = File(dir(context), "$id.png")
                if (id.isEmpty() || !file.exists()) return@mapNotNull null
                SavedSticker(
                    id = id,
                    subject = obj.optString("subject"),
                    title = obj.optString("title").ifBlank { null },
                    createdAt = obj.optLong("createdAt"),
                    file = file
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sticker index", e)
            emptyList()
        }
    }

    private fun writeIndex(context: Context, stickers: List<SavedSticker>) {
        val array = JSONArray()
        stickers.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("subject", it.subject)
                    .put("title", it.title ?: "")
                    .put("createdAt", it.createdAt)
            )
        }
        File(dir(context), INDEX_FILE).writeText(array.toString())
    }
}
