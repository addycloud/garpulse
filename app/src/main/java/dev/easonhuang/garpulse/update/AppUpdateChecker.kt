package dev.easonhuang.garpulse.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * In-app update check. The release is published on GitHub and mirrored on Gitee; on check we
 * probe both, pick the reachable one with the lower latency, and download the APK from it —
 * so users in mainland China are not blocked by GitHub being unreachable or slow.
 */
object AppUpdateChecker {

    data class Latest(
        val versionName: String,
        val apkUrl: String,
        val mirror: String,
        val notes: String,
        /** Raw tag (e.g. "v1.2.8-dev"), used to label the channel in the UI. */
        val tagName: String = "v$versionName",
    )

    /** Probe results in ms; -1 means unreachable within the timeout. */
    data class Probes(val github: Long, val gitee: Long) {
        val fastest: String?
            get() = when {
                github < 0 && gitee < 0 -> null
                gitee < 0 -> "github"
                github < 0 -> "gitee"
                else -> if (gitee <= github) "gitee" else "github"
            }
    }

    private const val GITHUB_API = "https://api.github.com/repos/addycloud/garpulse/releases/latest"
    private const val GITHUB_LIST = "https://api.github.com/repos/addycloud/garpulse/releases?per_page=15"
    private const val GITEE_API = "https://gitee.com/api/v5/repos/addycloud/GarPulse/releases/latest"
    private const val GITEE_LIST = "https://gitee.com/api/v5/repos/addycloud/GarPulse/releases?per_page=15"

    /**
     * Debug builds track the dev channel: the newest prerelease whose tag ends with "-dev".
     * Release builds track the stable channel: the newest non-prerelease.
     */
    suspend fun probe(): Probes = withContext(Dispatchers.IO) {
        Probes(measure(GITHUB_API), measure(GITEE_API))
    }

    private fun measure(url: String): Long = try {
        val start = System.currentTimeMillis()
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.responseCode
        System.currentTimeMillis() - start
    } catch (_: Exception) {
        -1L
    }

    private fun headers(mirror: String): Map<String, String> = when (mirror) {
        "github" -> mapOf("Accept" to "application/json", "User-Agent" to "garpulse")
        else -> mapOf("User-Agent" to "garpulse")
    }

    /**
     * Latest build of the requested channel. Both mirrors can carry both channels (the dev
     * prerelease is published on GitHub and mirrored to Gitee), so a user who cannot reach
     * GitHub can still be offered the debug build — and vice versa.
     */
    suspend fun fetchLatest(mirror: String, debugChannel: Boolean = false): Latest? =
        withContext(Dispatchers.IO) {
            try {
                val single = if (mirror == "github") GITHUB_API else GITEE_API
                val list = if (mirror == "github") GITHUB_LIST else GITEE_LIST
                val h = headers(mirror)
                if (!debugChannel) {
                    // The "latest" endpoint may still return a prerelease (Gitee does not always
                    // exclude them), so verify and fall back to a scan of the list.
                    val latest = runCatching { JSONObject(httpGet(single, h)).toLatest(mirror) }.getOrNull()
                    if (latest != null && !latest.tagName.endsWith("-dev")) return@withContext latest
                    return@withContext releases(mirror, h).firstOrNull { !it.tagName.endsWith("-dev") }
                }
                releases(mirror, h).firstOrNull { it.tagName.endsWith("-dev") }
            } catch (_: Exception) {
                null
            }
        }

    /** All releases from the list endpoint, newest first (prereleases included). */
    private fun releases(mirror: String, headers: Map<String, String>): List<Latest> {
        val listUrl = if (mirror == "github") GITHUB_LIST else GITEE_LIST
        val arr = JSONArray(httpGet(listUrl, headers))
        return (0 until arr.length()).mapNotNull { runCatching { arr.getJSONObject(it).toLatest(mirror) }.getOrNull() }
    }

    private fun JSONObject.toLatest(mirror: String): Latest? {
        val tag = optString("tag_name")
        if (tag.isBlank()) return null
        val asset = optJSONArray("assets")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
        }
        val url = asset?.optString("browser_download_url").orEmpty()
        if (url.isBlank()) return null
        return Latest(
            versionName = tag.removePrefix("v").removeSuffix("-dev"),
            apkUrl = url,
            mirror = mirror,
            notes = optString("body"),
            tagName = tag,
        )
    }

    /** Streams the APK into the cache; returns the file, or null on failure. */
    suspend fun download(url: String, target: File): File? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            if (c.responseCode !in 200..299) return@withContext null
            c.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            if (target.length() <= 0) null else target
        } catch (_: Exception) {
            null
        }
    }

    /** True when [remote] (e.g. "1.2.6") is strictly newer than [current] (e.g. "1.2.5"). */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').mapNotNull { it.toIntOrNull() }
        val a = parts(remote); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun httpGet(url: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}
