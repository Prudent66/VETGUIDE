package com.prudent66.vetguide.domain.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode-3 tier stub conformance (roadmap §5): explicit failure, no fabrication. */
class OnDeviceAiProviderTest {

    @Test
    fun `not-implemented tier always returns an explicit failure`() = runBlocking {
        val provider = OnDeviceAiProvider()

        val attempt = provider.analyze(AiRequest("Goat, adult", "sudden deaths in flock"))

        assertTrue(attempt is AiAttempt.Failure)
        assertTrue((attempt as AiAttempt.Failure).message.contains("not implemented"))
    }

    @Test
    fun `provider id stays stable for provenance and breaker bookkeeping`() {
        assertEquals(AiCatalog.ON_DEVICE_PROVIDER_ID, OnDeviceAiProvider().id)
    }
}
