package com.sachit.moneypal.data.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.asLog
import logcat.logcat
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores compressed receipt photos under the app-private files directory
 * (`files/receipts/`). Images are downscaled and re-encoded as JPEG so a
 * receipt never bloats the app's storage; the stored [Transaction.attachmentUri]
 * is a plain file path that survives app restarts without persisting URI
 * permissions.
 */
@Singleton
class AttachmentStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    companion object {
        private const val DIR_NAME = "receipts"
        private const val MAX_DIMENSION = 1600
        private const val JPEG_QUALITY = 82
    }

    fun attachmentsDir(): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Copies the image at [sourceUri] into the receipts directory, downscaled
     * and re-compressed. Returns the absolute file path to store on the
     * transaction, or null when the source cannot be decoded.
     */
    suspend fun importReceipt(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@runCatching null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_DIMENSION * 2 ||
                bounds.outHeight / sampleSize > MAX_DIMENSION * 2
            ) {
                sampleSize *= 2
            }

            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@runCatching null

            val scaled = scaleDown(bitmap, MAX_DIMENSION)
            if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()

            val target = File(attachmentsDir(), "receipt_${UUID.randomUUID()}.jpg")
            target.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()

            logcat { "Receipt imported: ${target.absolutePath}" }
            target.absolutePath
        }.onFailure {
            logcat { "Failed to import receipt\n${it.asLog()}" }
        }.getOrNull()
    }

    /**
     * Deletes the receipt file at [attachmentPath] when it lives inside the
     * receipts directory. Never throws — deletion is best-effort cleanup.
     */
    suspend fun deleteReceipt(attachmentPath: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = attachmentsDir()
            val file = File(attachmentPath)
            if (file.absolutePath.startsWith(dir.absolutePath) && file.exists()) {
                file.delete()
                logcat { "Receipt deleted: ${file.absolutePath}" }
            }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
