package com.prudent66.vetguide.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Short banner reasons that distinguish slow networks from provider outages. */
class FallbackReasonTest {

    @Test
    fun `timeouts map to a network timeout reason`() {
        assertEquals(
            "Network timeout",
            fallbackReason(
                listOf(
                    "[cloud-backend] network failure reaching the backend: " +
                        "Request timeout has expired",
                ),
            ),
        )
    }

    @Test
    fun `missing connectivity and DNS failures map to no internet`() {
        assertEquals(
            "No internet connection",
            fallbackReason(listOf("[cloud-backend] network access unavailable: denied")),
        )
        assertEquals(
            "No internet connection",
            fallbackReason(listOf("[cloud-backend] java.net.UnknownHostException")),
        )
    }

    @Test
    fun `provider errors keep the HTTP class visible`() {
        assertEquals(
            "Cloud API HTTP 5xx",
            fallbackReason(listOf("[cloud-backend] backend returned HTTP 503")),
        )
        assertEquals(
            "Cloud API HTTP 429 (rate limited)",
            fallbackReason(listOf("[cloud-backend] backend returned HTTP 429")),
        )
    }

    @Test
    fun `inconclusive and paused providers get specific reasons`() {
        assertEquals(
            "Cloud response was inconclusive",
            fallbackReason(listOf("[cloud-backend] insufficient information")),
        )
        assertEquals(
            "Cloud Analysis temporarily paused",
            fallbackReason(listOf("[cloud-backend] skipped — cooling down after failures")),
        )
    }

    @Test
    fun `dns and reachability failures get actionable reasons`() {
        assertEquals(
            "DNS lookup failed — check mobile data",
            fallbackReason(listOf("[cloud-backend] DNS lookup failed — check mobile data")),
        )
        assertEquals(
            "Server unreachable — slow or unstable signal",
            fallbackReason(listOf("[cloud-backend] Server unreachable — slow or unstable signal")),
        )
        // Both are transient mobile-data conditions, so they stay queueable.
        assertTrue(isTransientNetworkReason("DNS lookup failed — check mobile data"))
        assertTrue(isTransientNetworkReason("Server unreachable — slow or unstable signal"))
    }

    @Test
    fun `upstream capacity and rate limit responses stay queueable`() {
        assertEquals(
            "Cloud API HTTP 429 (rate limited)",
            fallbackReason(listOf("[cloud-backend] maximum capacity reached")),
        )
        assertEquals(
            "Cloud API HTTP 429 (rate limited)",
            fallbackReason(listOf("[cloud-backend] all provider keys exhausted")),
        )
        assertTrue(isQueueableFallbackReason("Cloud API HTTP 429 (rate limited)"))
        assertTrue(isQueueableFallbackReason("Cloud API HTTP 5xx"))
        assertTrue(isQueueableFallbackReason("Cloud Analysis unavailable"))
    }

    @Test
    fun `unknown failures fall back to a generic reason`() {
        assertEquals("Cloud Analysis unavailable", fallbackReason(emptyList()))
        assertEquals("Cloud Analysis unavailable", fallbackReason(listOf("scripted failure")))
    }
}
