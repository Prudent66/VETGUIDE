package com.prudent66.vetguide.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.prudent66.vetguide.domain.ai.AiCatalog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Live connectivity probe result shown on the Settings card. */
sealed interface CloudHealthResult {
    data class Healthy(
        val status: String,
        val mode: String?,
        val elapsedMs: Long,
    ) : CloudHealthResult

    data class Unhealthy(
        val message: String,
        val elapsedMs: Long,
    ) : CloudHealthResult
}

/**
 * Calls the Worker's `/health` endpoint with a short, UI-friendly timeout
 * (independent of the 45 s analysis budget) and measures round-trip time.
 * Expected failures are returned, never thrown, so Settings can show the
 * specific cause (timeout, DNS/internet, HTTP status).
 */
@Singleton
class CloudHealthClient @Inject constructor(
    private val client: HttpClient,
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(baseUrl: String = AiCatalog.BACKEND_BASE_URL): CloudHealthResult {
        val startedNs = System.nanoTime()
        fun elapsedMs(): Long = (System.nanoTime() - startedNs) / NANOS_PER_MILLI
        return try {
            val response = client.get("$baseUrl/health") {
                timeout {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }
            val body = response.bodyAsText()
            if (response.status.value in 200..299) {
                val (status, mode) = parseDetails(body)
                CloudHealthResult.Healthy(
                    status = status ?: "ok",
                    mode = mode,
                    elapsedMs = elapsedMs(),
                )
            } else {
                CloudHealthResult.Unhealthy(
                    message = "Cloud API HTTP ${response.status.value}",
                    elapsedMs = elapsedMs(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            android.util.Log.w("CloudHealthClient", "Health probe timed out", e)
            CloudHealthResult.Unhealthy("Network timeout", elapsedMs())
        } catch (e: IOException) {
            CloudHealthResult.Unhealthy(networkMessage(e), elapsedMs())
        } catch (e: SecurityException) {
            android.util.Log.w("CloudHealthClient", "Health probe blocked", e)
            CloudHealthResult.Unhealthy("No internet connection", elapsedMs())
        }
    }

    private fun parseDetails(body: String): Pair<String?, String?> = runCatching {
        val obj = json.parseToJsonElement(body).jsonObject
        val status = obj["status"]?.jsonPrimitive?.contentOrNull
        val mode = obj["mode"]?.jsonPrimitive?.contentOrNull
        status to mode
    }.getOrDefault(null to null)

    /** True when ConnectivityManager reports an active internet capability. */
    private fun hasActiveInternet(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
        val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun networkMessage(error: IOException): String {
        // A phone with mobile data active but a failing handshake is NOT offline.
        if (!hasActiveInternet()) return "No internet connection"
        return when {
            error is SocketTimeoutException -> "Network timeout"
            error is UnknownHostException -> "DNS lookup failed — check mobile data"
            error is ConnectException -> "Server unreachable — slow or unstable signal"
            error is SSLException -> "Server unreachable — slow or unstable signal"
            error.message?.contains("timeout", ignoreCase = true) == true -> "Network timeout"
            error.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                "DNS lookup failed — check mobile data"
            else -> "Network unreachable (${error.message ?: error.javaClass.simpleName})"
        }
    }

    private companion object {
        /** Slow cellular handshakes need a longer budget than the old 10/15 s. */
        const val REQUEST_TIMEOUT_MS = 25_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
