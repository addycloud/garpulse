package dev.easonhuang.garpulse.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Copies a user-picked image (content:// or file://) into the app's private storage and hands
 * widgets a stable internal path. Widgets render from that path via a down-sampled bitmap, so no
 * URI permission needs to survive a reboot or a process restart.
 */
object WidgetImageStore {

    private const val DIR = "widget_images"
    private const val MAX_DIMENSION = 720

    private fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** Imports [uri] into private storage and returns the new internal file path, or null. */
    fun import(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val target = File(directory(context), "img_${UUID.randomUUID()}.jpg")
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            } ?: return null
            target.absolutePath
        }.getOrNull()
    }

    /** Decodes a stored image into a memory-safe bitmap sized for a widget. */
    fun load(path: String?, maxDimension: Int = MAX_DIMENSION): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = max(1, max(bounds.outWidth, bounds.outHeight) / maxDimension)
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, decode)
        }.getOrNull()
    }

    /** Removes a stored image; safe to call on a missing file. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /**
     * Returns a copy of [bitmap] painted at [alpha] (0 = invisible, 1 = untouched). RemoteViews
     * cannot set a view-level alpha, so the widget's "picture transparency" is baked into the
     * bitmap instead — one mechanism, identical on the home screen and in the in-app preview.
     */
    fun withAlpha(bitmap: Bitmap, alpha: Float): Bitmap {
        val a = alpha.coerceIn(0f, 1f)
        if (a >= 1f) return bitmap
        if (a <= 0f) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.alpha = (a * 255f).toInt().coerceIn(0, 255)
        })
        return out
    }
}
