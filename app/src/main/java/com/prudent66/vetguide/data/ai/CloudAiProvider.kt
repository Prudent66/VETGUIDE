package com.prudent66.vetguide.data.ai

import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.AiAnalysisRequest
import com.prudent66.vetguide.domain.ai.AiAttempt
import com.prudent66.vetguide.domain.ai.AiCatalog
import com.prudent66.vetguide.domain.ai.AiProvider
import com.prudent66.vetguide.domain.ai.AiRequest
import com.prudent66.vetguide.domain.ai.toNarrativeText
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Cloud tier (roadmap §5.1 modes 1–2, §6): all calls route through the secure
 * VETGUIDE backend (Cloudflare Workers) — never directly to AI vendors, so the
 * APK holds no keys (§8).
 *
 * Contract (§5/§6, mirrors the AiProvider docs): never throws for an expected
 * refusal — network errors, HTTP 401/5xx, and schema violations all map to
 * [AiAttempt.Failure] so the orchestrator ladder can fail over (on-device
 * tier, then the mode-4 deterministic fallback). The one deliberate exception
 * is HTTP 429: [AiQuotaExceededException] is thrown so the UI can show a
 * specific capacity message instead of silently degrading. Cancellation still
 * propagates.
 */
class CloudAiProvider(
    override val id: String = AiCatalog.CLOUD_PROVIDER_ID,
    private val baseUrl: String = AiCatalog.BACKEND_BASE_URL,
    private val client: HttpClient,
) : AiProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(request: AiRequest): AiAttempt = try {
        val response = client.post("$baseUrl/analyze") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AiAnalysisRequest(
                        imageBase64 = request.imageBase64,
                        prompt = request.findings,
                        clinicalContext = request.clinicalContext,
                    ),
                ),
            )
        }
        val body = response.bodyAsText()
        analyzeStatus(response.status, body)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiQuotaExceededException) {
        throw e // propagate to UI for specific message
    } catch (e: SecurityException) {
        // Defense in depth: e.g. a build without the INTERNET permission must
        // degrade to the failover ladder, never crash the app (§5.2).
        AiAttempt.Failure("[$id] network access unavailable: ${e.message}")
    } catch (e: SocketTimeoutException) {
        // Socket/write timeout on a slow upload — one compact 30 KB retry.
        tryCompactRetry(request, e)
    } catch (e: HttpRequestTimeoutException) {
        // Ktor's own request timeout (45 s ceiling) also warrants the retry.
        tryCompactRetry(request, e)
    } catch (e: UnknownHostException) {
        // Active mobile data but no DNS answer — never bench the cloud tier.
        AiAttempt.Failure("[$id] DNS lookup failed — check mobile data (${e.message})")
    } catch (e: ConnectException) {
        AiAttempt.Failure("[$id] Server unreachable — slow or unstable signal (${e.message})")
    } catch (e: SSLException) {
        AiAttempt.Failure("[$id] Server unreachable — slow or unstable signal (${e.message})")
    } catch (e: IOException) {
        // Engine-specific timeout messages that did not map to a typed
        // exception still get the compact retry; other I/O errors (unknown
        // host, resets) surface as failures for the failover ladder.
        if (e.message?.contains("timeout", ignoreCase = true) == true) {
            tryCompactRetry(request, e)
        } else {
            AiAttempt.Failure("[$id] network failure reaching the backend: ${e.message}")
        }
    } catch (e: SerializationException) {
        AiAttempt.Failure("[$id] backend response failed schema validation: ${e.message}")
    }

    /** Maps one upstream HTTP status to an attempt; only 429/502/503/504 bench. */
    private fun analyzeStatus(status: HttpStatusCode, body: String): AiAttempt = when (status) {
        HttpStatusCode.OK -> decodeAttempt(body)
        HttpStatusCode.TooManyRequests -> throw AiQuotaExceededException(
            "[$id] cloud analysis at maximum capacity — all provider keys exhausted",
        )
        else -> {
            val code = status.value
            AiAttempt.Failure(
                "[$id] backend returned HTTP $code",
                breakerEligible = code in breakerHttpStatuses,
            )
        }
    }

    private fun decodeAttempt(body: String): AiAttempt {
        val data = json.decodeFromString<AiAnalysisData>(body)
        return if (data.suspectedConditions.isEmpty()) {
            // §10: insufficient information is an explicit failure, not a guess.
            AiAttempt.Failure("[$id] insufficient information — no suspected conditions returned")
        } else {
            AiAttempt.Success(text = data.toNarrativeText(), analysisData = data)
        }
    }

    /**
     * Attempts one compact retry (512px / 50% quality / ~30 KB) when a socket
     * timeout occurs on the primary upload. If the retry also fails, the
     * original timeout is propagated so the orchestrator can fail over.
     */
    private suspend fun tryCompactRetry(
        request: AiRequest,
        originalError: IOException,
    ): AiAttempt {
        val primaryBase64 = request.imageBase64
        if (primaryBase64 == null) {
            return AiAttempt.Failure(
                "[$id] network failure reaching the backend: ${originalError.message}",
            )
        }
        val compactBase64 = try {
            ImageEncoder.recompressForCompactRetry(primaryBase64)
        } catch (e: IllegalArgumentException) {
            // Corrupt payload cannot be re-encoded — let the ladder fail over.
            android.util.Log.w("CloudAiProvider", "Compact retry re-compress failed", e)
            null
        }
        return if (compactBase64 == null) {
            AiAttempt.Failure("[$id] network failure reaching the backend: ${originalError.message}")
        } else {
            sendCompactRequest(compactBase64, request, originalError)
        }
    }

    /** Sends the compact Base64 payload once; mirrors [analyze]'s error mapping. */
    private suspend fun sendCompactRequest(
        compactBase64: String,
        request: AiRequest,
        originalError: IOException,
    ): AiAttempt = try {
        val response = client.post("$baseUrl/analyze") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AiAnalysisRequest(
                        imageBase64 = compactBase64,
                        prompt = request.findings,
                        clinicalContext = request.clinicalContext,
                    ),
                ),
            )
        }
        val body = response.bodyAsText()
        when (response.status) {
            HttpStatusCode.OK -> decodeAttempt(body)
            HttpStatusCode.TooManyRequests ->
                AiAttempt.Failure("[$id] cloud analysis at maximum capacity — all provider keys exhausted")
            else -> {
                val status = response.status.value
                AiAttempt.Failure(
                    "[$id] compact retry backend returned HTTP $status",
                    breakerEligible = status in breakerHttpStatuses,
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        AiAttempt.Failure("[$id] compact retry network access unavailable: ${e.message}")
    } catch (e: IOException) {
        // Compact retry also failed — propagate the original timeout so the
        // orchestrator ladder (on-device → deterministic-offline) proceeds.
        android.util.Log.w("CloudAiProvider", "Compact retry failed", e)
        AiAttempt.Failure("[$id] network failure reaching the backend: ${originalError.message}")
    } catch (e: SerializationException) {
        android.util.Log.w("CloudAiProvider", "Compact retry schema validation failed", e)
        AiAttempt.Failure("[$id] compact retry backend response failed schema validation: ${e.message}")
    }

    private companion object {
        /** Only these upstream HTTP responses may trip the circuit breaker. */
        val breakerHttpStatuses = setOf(429, 502, 503, 504)
    }
}
