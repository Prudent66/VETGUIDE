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
    return when {
        text.contains("timed out") || text.contains("timeout") -> "Network timeout"
        text.contains("network access unavailable") -> "No internet connection"
        text.contains("unknownhost") ||
            text.contains("unable to resolve host") ||
            text.contains("no address associated") -> "No internet connection"
        text.contains("http 429") || text.contains("maximum capacity") ->
            "Cloud API HTTP 429 (rate limited)"
        Regex("""http 5\d\d""").containsMatchIn(text) -> "Cloud API HTTP 5xx"
        text.contains("insufficient information") -> "Cloud response was inconclusive"
        text.contains("schema validation") -> "Cloud response was invalid"
        text.contains("network failure") -> "Network unreachable"
        text.contains("cooling down") || text.contains("benched") ->
            "Cloud AI temporarily paused"
        else -> "Cloud AI unavailable"
    }
}
