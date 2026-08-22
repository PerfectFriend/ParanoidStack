/**
 * Image compression utilities for reducing attachment sizes before XFTP upload.
 * Downsizes images to a maximum dimension of 1920 px and applies JPEG compression at 80% quality.
 */
package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Compresses images to reduce bandwidth and storage usage during file transfer.
 * Uses inSampleSize downscaling followed by JPEG encoding.
 */
class ImageCompressor {
    companion object {
        private const val MAX_DIMENSION = 1920
        private const val JPEG_QUALITY = 80
    }

    data class CompressedImage(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val sizeBytes: Int
    )

    fun compress(context: Context, uri: Uri): CompressedImage? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val scale = calculateScale(options.outWidth, options.outHeight)
            val sampleOptions = BitmapFactory.Options().apply { inSampleSize = scale }
            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, sampleOptions)
            inputStream2.close()

            val stream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            val bytes = stream.toByteArray()

            CompressedImage(bytes, bitmap?.width ?: 0, bitmap?.height ?: 0, bytes.size)
        } catch (_: Exception) { null }
    }

    fun compressToFile(context: Context, uri: Uri, outputFile: File): Boolean {
        val compressed = compress(context, uri) ?: return false
        return try {
            FileOutputStream(outputFile).use { it.write(compressed.bytes) }
            true
        } catch (_: Exception) { false }
    }

    private fun calculateScale(width: Int, height: Int): Int {
        val max = maxOf(width, height)
        var scale = 1
        while (max / scale > MAX_DIMENSION) scale *= 2
        return scale
    }
}
