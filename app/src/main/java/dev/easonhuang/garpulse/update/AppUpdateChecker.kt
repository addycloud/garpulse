package dev.easonhuang.garpulse.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    data class Latest(val versionName: String, val apkUrl: String, val mirror: String, val notes: String)

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
    private const val GITEE_API = "https://gitee.com/api/v5/repos/addycloud/GarPulse/releases/latest"

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

    suspend fun fetchLatest(mirror: String): Latest? = withContext(Dispatchers.IO) {
        try {
            when (mirror) {
                "github" -> {
                    val body = httpGet(GITHUB_API, headers = mapOf("Accept" to "application/json", "User-Agent" to "garpulse"))
                    val json = JSONObject(body)
                    val asset = json.optJSONArray("assets")?.let { arr ->
                        (0 until arr.length()).map { arr.getJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk") }
                    }
                    Latest(
                        versionName = json.optString("tag_name").removePrefix("v"),
                        apkUrl = asset?.optString("browser_download_url") ?: "",
                        mirror = mirror,
                        notes = json.optString("body"),
                    )
                }
                else -> {
                    val body = httpGet(GITEE_API, headers = mapOf("User-Agent" to "garpulse"))
                    val json = JSONObject(body)
                    val asset = json.optJSONArray("assets")?.let { arr ->
                        (0 until arr.length()).map { arr.getJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk") }
                            ?.let { it.optString("browser_download_url") }
                    }
                    Latest(
                        versionName = json.optString("tag_name").removePrefix("v"),
                        apkUrl = asset ?: "",
                        mirror = mirror,
                        notes = json.optString("body"),
                    )
                }
            }.takeIf { it.apkUrl.isNotBlank() }
        } catch (_: Exception) {
            null
        }
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
