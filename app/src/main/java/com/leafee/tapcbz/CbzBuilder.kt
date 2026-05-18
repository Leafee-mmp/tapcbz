package com.leafee.tapcbz

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CbzBuilder {

    data class Result(
        val success: Boolean,
        val filename: String = "",
        val error: String = ""
    )

    fun build(
        context: Context,
        images: List<ImageItem>,
        cbzName: String,
        onProgress: (Int, Int) -> Unit
    ): Result {
        return try {
            val outputStream = openOutputStream(context, cbzName)
                ?: return Result(false, error = "Could not open output stream for $cbzName")

            outputStream.use { os ->
                ZipOutputStream(os.buffered()).use { zip ->
                    images.forEachIndexed { index, item ->
                        onProgress(index + 1, images.size)
                        val ext = item.name.substringAfterLast('.', "jpg")
                        val entryName = "page_${String.format("%04d", index + 1)}.$ext"
                        try {
                            context.contentResolver.openInputStream(item.uri)?.use { input ->
                                zip.putNextEntry(ZipEntry(entryName))
                                BufferedInputStream(input).copyTo(zip)
                                zip.closeEntry()
                            }
                        } catch (e: Exception) {
                            // Skip unreadable files silently
                        }
                    }
                }
            }

            Result(success = true, filename = cbzName)
        } catch (e: Exception) {
            Result(success = false, error = e.message ?: "Unknown error")
        }
    }

    private fun openOutputStream(context: Context, cbzName: String): OutputStream? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, cbzName)
            put(MediaStore.Downloads.MIME_TYPE, "application/x-cbz")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return null

        val stream = context.contentResolver.openOutputStream(uri)

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)

        return stream
    }
}
