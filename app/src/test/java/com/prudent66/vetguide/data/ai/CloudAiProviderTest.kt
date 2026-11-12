package com.prudent66.vetguide.data.ai

import com.prudent66.vetguide.domain.ai.AiAttempt
import com.prudent66.vetguide.domain.ai.AiCatalog
import com.prudent66.vetguide.domain.ai.AiRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract conformance suite for the cloud tier (roadmap §5/§6): mocked HTTP
 * only — no real billable endpoints in CI (§7). Proves the failure contract:
 * HTTP 401/429/5xx, schema violations, and network errors all surface as
 * AiAttempt.Failure (never a throw), so the orchestrator ladder can fail over.
 */
class CloudAiProviderTest {

    private val request = AiRequest("Dog, 4 months", "vomiting; hemorrhagic diarrhea")

    private val validBody = """
        {"provider":"gemini","model":"gemini-flash","findings":["vomiting"],
         "suspectedConditions":[{"name":"Canine Parvovirus","confidence":0.62}],
         "recommendedNextSteps":["Isolate; contact the attending veterinarian"]}
    """.trimIndent()

    private fun mockClient(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine) { engine { addHandler(handler) } }

    private fun jsonHandler(body: String, status: HttpStatusCode): Pair<HttpClient, MutableList<HttpRequestData>> {
        val seen = mutableListOf<HttpRequestData>()
        val client = mockClient { request ->
            seen += request
            respond(body, status, headersOf("Content-Type" to listOf("application/json")))
        }
        return client to seen
    }

    @Test
    fun `success parses structured output and carries provenance`() = runBlocking {
        val (client, _) = jsonHandler(validBody, HttpStatusCode.OK)
        val provider = CloudAiProvider(client = client)

        val attempt = provider.analyze(request)

        assertTrue(attempt is AiAttempt.Success)
        val success = attempt as AiAttempt.Success
        assertTrue(success.text.contains("gemini/gemini-flash"))
        assertTrue(success.text.contains("Canine Parvovirus"))
        assertTrue(success.text.contains("requires veterinary review"))
        // The structured object is carried through for sectioned UI rendering.
        val data = success.analysisData
        assertTrue(data != null)
        assertEquals("gemini-flash", data!!.model)
        assertEquals(listOf("vomiting"), data.findings)
        assertEquals("Canine Parvovirus", data.suspectedConditions.single().name)
        assertEquals(
            listOf("Isolate; contact the attending veterinarian"),
            data.recommendedNextSteps,
        )
    }

    @Test
    fun `posts JSON payload to the backend analyze endpoint`() = runBlocking {
        val (client, seen) = jsonHandler(validBody, HttpStatusCode.OK)
        val provider = CloudAiProvider(baseUrl = "https://backend.test", client = client)

        provider.analyze(request)

        assertEquals(1, seen.size)
        assertEquals(HttpMethod.Post, seen[0].method)
        assertTrue(seen[0].url.encodedPath.endsWith("/analyze"))
    }

    @Test
    fun `http auth rate-limit and server errors map to failure`() = runBlocking {
        listOf(
            HttpStatusCode.Unauthorized,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.BadGateway,
        ).forEach { status ->
            val (client, _) = jsonHandler(validBody, status)
            val provider = CloudAiProvider(client = client)

            val attempt = provider.analyze(request)

            assertTrue("HTTP $status must be a Failure", attempt is AiAttempt.Failure)
            val failure = attempt as AiAttempt.Failure
            assertTrue(failure.message.contains("HTTP ${status.value}"))
            assertTrue(failure.message.contains(AiCatalog.CLOUD_PROVIDER_ID))
        }
    }

    @Test
    fun `empty suspected conditions is an explicit insufficient-information failure`() =
        runBlocking {
            val body = """
                {"provider":"gemini","model":"gemini-flash","findings":["vomiting"],
                 "suspectedConditions":[]}
            """.trimIndent()
            val (client, _) = jsonHandler(body, HttpStatusCode.OK)
            val provider = CloudAiProvider(client = client)

            val attempt = provider.analyze(request)

            assertTrue(attempt is AiAttempt.Failure)
            assertTrue(
                (attempt as AiAttempt.Failure).message.contains("insufficient information"),
            )
        }

    @Test
    fun `malformed schema response maps to failure not a throw`() = runBlocking {
        val (client, _) = jsonHandler("{\"suspectedConditions\":\"not-a-list\"}", HttpStatusCode.OK)
        val provider = CloudAiProvider(client = client)

        val attempt = provider.analyze(request)

        assertTrue(attempt is AiAttempt.Failure)
        assertTrue(
            (attempt as AiAttempt.Failure).message.contains("schema validation"),
        )
    }

    @Test
    fun `network failure surfaces as failure without throwing`() = runBlocking {
        val client = mockClient { throw IOException("connection reset") }
        val provider = CloudAiProvider(client = client)

        val attempt = provider.analyze(request)

        assertTrue(attempt is AiAttempt.Failure)
        assertTrue((attempt as AiAttempt.Failure).message.contains("network failure"))
    }
}
