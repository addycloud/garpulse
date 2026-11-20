package dev.easonhuang.heartwood.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

const val GARMIN_BASE_URL: String = "https://personal.connectapi.garmin.com"
const val GARMIN_TOKEN_PATH: String = "/personalapitoken-service/personal-api/personal-api-key/token"

class GarminApiException(message: String, val status: Int = 0) : IOException(message)

/** Reads the `exp` claim from a JWT. Returns 0 when the token is not a readable JWT. */
internal fun jwtExpirySeconds(jwt: String): Long {
    val parts = jwt.trim().split('.')
    if (parts.size < 2) return 0L
    return try {
        val payload = Base64.getUrlDecoder().decode(parts[1])
        JSONObject(String(payload, StandardCharsets.UTF_8)).optLong("exp", 0L)
    } catch (_: Exception) {
        0L
    }
}

/**
 * Minimal client for Garmin Connect Personal API. Uses the platform HTTP stack (no new dependency)
 * and the official two-step auth: exchange the user's Personal API key for a 24-hour bearer token,
 * then call data endpoints with that token. A premature 401 triggers one forced re-exchange.
 */
class GarminPersonalApi(private val prefs: MetricSourcePreferences) {

    /** Returns a cached token while it has over an hour left, otherwise requests a new one. */
    suspend fun token(force: Boolean = false): String = withContext(Dispatchers.IO) {
        val state = prefs.state.first()
        if (!force && state.tokenUsable) return@withContext state.token
        val key = state.apiKey.trim()
        if (key.isEmpty()) throw GarminApiException("No Garmin Personal API key configured")

        val connection = open(GARMIN_BASE_URL + GARMIN_TOKEN_PATH, "POST")
        try {
            connection.setRequestProperty("Authorization", "ApiKey $key")
            connection.setRequestProperty("Accept", "*/*")
            val body = connection.readBody()
            if (connection.responseCode !in 200..299) {
                throw GarminApiException("Token request failed (${connection.responseCode}): ${brief(body)}", connection.responseCode)
            }
            val jwt = body.trim()
            val expiresAt = jwtExpirySeconds(jwt)
            if (expiresAt <= 0L) throw GarminApiException("Garmin returned an unreadable access token")
            prefs.saveToken(jwt, expiresAt * 1000L)
            jwt
        } finally {
            connection.disconnect()
        }
    }

    /** GETs one endpoint as raw JSON text; empty successful responses become an empty string. */
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val query = params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        val url = GARMIN_BASE_URL + path + if (query.isEmpty()) "" else "?$query"
        var lastError: GarminApiException? = null
        repeat(2) { attempt ->
            val token = token(force = attempt > 0)
            val connection = open(url, "GET")
            try {
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                val body = connection.readBody()
                when (val code = connection.responseCode) {
                    in 200..299 -> return@withContext body
                    401 -> lastError = GarminApiException("Garmin access token expired", 401)
                    else -> throw GarminApiException("Garmin request failed ($code): ${brief(body)}", code)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw lastError ?: GarminApiException("Garmin request failed")
    }

    /** Forces a fresh token and reads the profile endpoint, used by the settings "Test" button. */
    suspend fun testConnection(): JSONObject = withContext(Dispatchers.IO) {
        val token = token(force = true)
        val connection = open(GARMIN_BASE_URL + "/userprofile-service/userprofile/settings", "GET")
        try {
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val body = connection.readBody()
            if (connection.responseCode !in 200..299) {
                throw GarminApiException("Garmin connection test failed (${connection.responseCode}): ${brief(body)}", connection.responseCode)
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }

    private fun HttpURLConnection.readBody(): String {
        val stream = runCatching { errorStream ?: inputStream }.getOrNull() ?: return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun brief(body: String): String = body.trim().take(180)
}
