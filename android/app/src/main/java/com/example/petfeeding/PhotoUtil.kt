package com.example.petfeeding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Helpers for turning a user-picked photo (content Uri) into a small, square image
 * stored in app-internal storage, and for loading it back (including a circular crop
 * for display). We copy into internal storage because the photo-picker Uri grant is
 * temporary and would stop working after a reboot.
 */
object PhotoUtil {

    private const val MAX_DIM = 512 // stored photo is at most 512x512

    private fun photoDir(context: Context): File =
        File(context.filesDir, "pet_photos").apply { if (!exists()) mkdirs() }

    /**
     * Decode [uri] (downsampled), center-crop to a square, cap at MAX_DIM, and save as a
     * JPEG in internal storage. Returns the absolute file path, or null on failure.
     */
    fun importPhoto(context: Context, uri: Uri, petKey: String): String? {
        return try {
            val resolver = context.contentResolver

            // First pass: bounds only, to compute a downsample factor.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIM)
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val square = centerCropSquare(decoded, MAX_DIM)
            if (square !== decoded) decoded.recycle()

            val file = File(photoDir(context), "pet_${petKey}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                square.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            square.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(w: Int, h: Int, target: Int): Int {
        var sample = 1
        var halfW = w / 2
        var halfH = h / 2
        while (halfW >= target && halfH >= target) {
            sample *= 2
            halfW /= 2
            halfH /= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun centerCropSquare(src: Bitmap, maxDim: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        if (side <= maxDim) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, maxDim, maxDim, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    /** Load the stored photo as a plain square bitmap, or null if missing/unreadable. */
    fun loadBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    /** Load the stored photo cropped into a circle, sized to [size] px. null if missing. */
    fun loadCircular(path: String?, size: Int): Bitmap? {
        val src = loadBitmap(path) ?: return null
        val scaled = if (src.width != size || src.height != size)
            Bitmap.createScaledBitmap(src, size, size, true) else src
        if (scaled !== src) src.recycle()

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawOval(RectF(rect), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, rect, rect, paint)
        scaled.recycle()
        return output
    }
}
