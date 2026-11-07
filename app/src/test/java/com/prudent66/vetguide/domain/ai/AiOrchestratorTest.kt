package com.prudent66.vetguide.domain.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fault-injection suite for the failover ladder + circuit breaker (§5 acceptance). */
class AiOrchestratorTest {

    private val request = AiRequest("Dog, 4 months", "vomiting; hemorrhagic diarrhea; lethargy")

    @Test
    fun `primary failure falls through to secondary provider`() = runBlocking {
        val primary = FakeAiProvider(id = "primary", failuresBeforeSuccess = 99)
        val secondary = FakeAiProvider(id = "secondary")
        val orchestrator = AiOrchestrator(listOf(primary, secondary))

        val result = orchestrator.analyze(request, nowMs = 1_000) as AiResult.ProviderSuccess

        assertEquals("secondary", result.providerId)
        assertEquals(1, primary.callCount)
        assertEquals(1, secondary.callCount)
    }

    @Test
    fun `all providers fail lands on deterministic offline fallback`() = runBlocking {
        val primary = FakeAiProvider(id = "primary", failuresBeforeSuccess = 99)
        val secondary = FakeAiProvider(id = "secondary", failuresBeforeSuccess = 99)
        val orchestrator = AiOrchestrator(listOf(primary, secondary))

        val result = orchestrator.analyze(request, nowMs = 1_000) as AiResult.DeterministicFallback

        assertEquals(OFFLINE_PROVIDER_ID, result.providerId)
        assertTrue(result.text.contains("requires veterinary review"))
        assertTrue(result.text.contains(request.findings))
        assertFalse(result.text.contains(primary.id))
    }

    @Test
    fun `circuit breaker benches provider after three consecutive failures`() = runBlocking {
        val primary = FakeAiProvider(id = "primary", failuresBeforeSuccess = 99)
        val secondary = FakeAiProvider(id = "secondary")
        val orchestrator = AiOrchestrator(listOf(primary, secondary))

        repeat(3) { orchestrator.analyze(request, nowMs = 1_000) }
        assertEquals(3, primary.callCount)

        // Breaker is open: the benched provider is skipped entirely.
        orchestrator.analyze(request, nowMs = 2_000)
        assertEquals(3, primary.callCount)
        assertEquals(4, secondary.callCount)
        assertTrue(orchestrator.isBenched("primary", nowMs = 2_000))
    }

    @Test
    fun `circuit breaker recovers once the bench window expires`() = runBlocking {
        val primary = FakeAiProvider(id = "primary", failuresBeforeSuccess = 99)
        val secondary = FakeAiProvider(id = "secondary")
        val orchestrator = AiOrchestrator(listOf(primary, secondary))

        repeat(3) { orchestrator.analyze(request, nowMs = 1_000) }

        // After the 5-minute default window the provider becomes eligible again.
        orchestrator.analyze(request, nowMs = 1_000 + (5 * 60 * 1000L) + 1)
        assertEquals(4, primary.callCount)
        assertFalse(orchestrator.isBenched("primary", nowMs = 1_000 + (5 * 60 * 1000L) + 1))
    }

    @Test
    fun `success resets failures so isolated errors never trip the breaker`() = runBlocking {
        val primary = FakeAiProvider(id = "primary", failuresBeforeSuccess = 1)
        val secondary = FakeAiProvider(id = "secondary")
        val orchestrator = AiOrchestrator(listOf(primary, secondary))
        var nowMs = 1_000L
        suspend fun step(): AiResult {
            nowMs += 1_000
            return orchestrator.analyze(request, nowMs = nowMs)
        }

        assertEquals("secondary", step().providerId) // fail #1 → ladder to secondary
        assertEquals("primary", step().providerId) // success resets the breaker
        assertEquals("secondary", step().providerId) // a single later failure is not enough
        assertEquals("primary", step().providerId) // provider stays in the ladder

        assertEquals(4, primary.callCount)
        assertEquals(2, secondary.callCount)
        assertFalse(orchestrator.isBenched("primary", nowMs = nowMs))
    }

    @Test
    fun `offline fallback is labeled rule-based with no AI provenance`() {
        val text = DeterministicFallbackBuilder.build(
            AiRequest("Goat, adult", "sudden deaths in flock; labored breathing"),
        )

        assertTrue(text.contains("Placeholder rule-based engine"))
        assertTrue(text.contains("requires veterinary review"))
    }
}
