package com.prudent66.vetguide.data.ai

import com.prudent66.vetguide.domain.ai.AiCatalog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
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

    private fun networkMessage(error: IOException): String = when {
        error is SocketTimeoutException -> "Network timeout"
        error is UnknownHostException -> "No internet connection"
        error.message?.contains("timeout", ignoreCase = true) == true -> "Network timeout"
        error.message?.contains("unable to resolve host", ignoreCase = true) == true ->
            "No internet connection"
        else -> "Network unreachable (${error.message ?: error.javaClass.simpleName})"
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
