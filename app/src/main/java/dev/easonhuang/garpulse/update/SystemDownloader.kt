package dev.easonhuang.garpulse.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri

/**
 * Hands package downloads to the platform downloader instead of streaming them inside the app.
 *
 * The system downloader brings its own notification with progress, survives the app being
 * killed, and its notification opens the installer with the right URI grants — none of which
 * the app has to reimplement (and get wrong).
 */
object SystemDownloader {

    fun enqueue(context: Context, url: String, fileName: String, title: String): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return false
        return runCatching {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(title)
                .setDescription(fileName)
                .setMimeType(APK_MIME)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            manager.enqueue(request)
            true
        }.getOrElse { error ->
            android.util.Log.e("GarPulseUpdate", "download enqueue failed", error)
            false
        }
    }

    private const val APK_MIME = "application/vnd.android.package-archive"
}
