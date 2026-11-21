package com.prudent66.vetguide.domain.ai

import org.junit.Assert.assertEquals
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
            "Cloud AI temporarily paused",
            fallbackReason(listOf("[cloud-backend] skipped — cooling down after failures")),
        )
    }

    @Test
    fun `unknown failures fall back to a generic reason`() {
        assertEquals("Cloud AI unavailable", fallbackReason(emptyList()))
        assertEquals("Cloud AI unavailable", fallbackReason(listOf("scripted failure")))
    }
}
