package com.prudent66.vetguide.domain.ai

/**
 * Turns raw orchestrator diagnostics into one short, user-facing reason for
 * the deterministic-offline banner (roadmap §5.2/§10). The banner lets the vet
 * distinguish a slow rural upload or VPN block ("Network timeout",
 * "No internet connection") from a provider-side outage ("HTTP 5xx/429").
 * Diagnostics are intentionally ordered by provider attempt, so the first
 * matching cause is the most relevant one.
 */
fun fallbackReason(diagnostics: List<String>): String {
    val text = diagnostics.joinToString(" ").lowercase()
    return reasonMarkers
        .firstOrNull { (markers, _) -> markers.any(text::contains) }
        ?.second
        ?: "Cloud AI unavailable"
}

/** Ordered cause → short banner reason; the first matching marker wins. */
private val reasonMarkers: List<Pair<List<String>, String>> = listOf(
    listOf("timed out", "timeout") to "Network timeout",
    listOf("network access unavailable") to "No internet connection",
    listOf("unknownhost", "unable to resolve host", "no address associated") to
        "No internet connection",
    listOf("http 429", "maximum capacity") to "Cloud API HTTP 429 (rate limited)",
    listOf("http 5") to "Cloud API HTTP 5xx",
    listOf("insufficient information") to "Cloud response was inconclusive",
    listOf("schema validation") to "Cloud response was invalid",
    listOf("network failure") to "Network unreachable",
    listOf("cooling down", "benched") to "Cloud AI temporarily paused",
)
