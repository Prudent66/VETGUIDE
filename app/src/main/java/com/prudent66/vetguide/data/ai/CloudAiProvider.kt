package com.prudent66.vetguide.data.ai

import com.prudent66.vetguide.data.ai.ImageEncoder
import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.AiAnalysisRequest
import com.prudent66.vetguide.domain.ai.AiAttempt
import com.prudent66.vetguide.domain.ai.AiCatalog
import com.prudent66.vetguide.domain.ai.AiProvider
import com.prudent66.vetguide.domain.ai.AiRequest
import com.prudent66.vetguide.domain.ai.toNarrativeText
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import java.net.SocketTimeoutException
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
        when (response.status) {
            HttpStatusCode.OK -> decodeAttempt(body)
            HttpStatusCode.TooManyRequests -> throw AiQuotaExceededException(
                "[$id] AI service at maximum capacity — all provider keys exhausted",
            )
            else -> AiAttempt.Failure("[$id] backend returned HTTP ${response.status.value}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiQuotaExceededException) {
        throw e // propagate to UI for specific message
    } catch (e: SecurityException) {
        // Defense in depth: e.g. a build without the INTERNET permission must
        // degrade to the failover ladder, never crash the app (§5.2).
        AiAttempt.Failure("[$id] network access unavailable: ${e.message}")
    } catch (e: SocketTimeoutException) {
        // Socket timeout (read/write) on slow upload — attempt one compact retry
        // (512px / 50% quality / ~30 KB) before giving up to the failover ladder.
        return tryCompactRetry(request, e)
    } catch (e: IOException) {
        // UnknownHostException / connection resets / other I/O errors surface
        // as failures so the orchestrator ladder can proceed (§5.2).
        AiAttempt.Failure("[$id] network failure reaching the backend: ${e.message}")
    } catch (e: SerializationException) {
        AiAttempt.Failure("[$id] backend response failed schema validation: ${e.message}")
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
        originalError: SocketTimeoutException,
    ): AiAttempt {
        val compactBase64 = ImageEncoder.recompressForCompactRetry(request.imageBase64)
        return try {
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
                HttpStatusCode.TooManyRequests -> throw AiQuotaExceededException(
                    "[$id] AI service at maximum capacity — all provider keys exhausted",
                )
                else -> AiAttempt.Failure(
                    "[$id] compact retry backend returned HTTP ${response.status.value}",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiQuotaExceededException) {
            throw e
        } catch (e: SecurityException) {
            AiAttempt.Failure("[$id] compact retry network access unavailable: ${e.message}")
        } catch (e: IOException) {
            // Compact retry also failed — propagate the original timeout so the
            // orchestrator ladder (on-device → deterministic-offline) proceeds.
            AiAttempt.Failure("[$id] network failure reaching the backend: ${originalError.message}")
        } catch (e: SerializationException) {
            AiAttempt.Failure("[$id] compact retry backend response failed schema validation: ${e.message}")
        }
    }
}
