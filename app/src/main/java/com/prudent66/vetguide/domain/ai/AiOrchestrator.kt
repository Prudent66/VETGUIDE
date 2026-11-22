package com.prudent66.vetguide.domain.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * Failover ladder over a priority provider list (roadmap §5.2): providers are
 * tried in order with max one attempt per provider per request. Only upstream
 * HTTP capacity/server failures ([AiAttempt.Failure.breakerEligible]) count
 * toward benching — local DNS/socket/timeout errors never open the breaker,
 * so a weak mobile signal cannot surface "Cloud AI temporarily paused".
 * Any cooldown is capped at [MAX_COOLDOWN_MS] (15 s).
 *
 * Cloud AI analysis is always attempted: Settings no longer exposes an
 * opt-out, so the ladder runs on every call. When every provider is
 * unreachable, out of quota, or benched, the deterministic mode-4 fallback
 * (questionnaire + offline KB) answers — which is never an AiProvider (§5.1).
 */
class AiOrchestrator(
    private val providers: List<AiProvider>,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val benchMs: Long = DEFAULT_BENCH_MS,
) {

    private class BreakerState {
        var consecutiveFailures = 0
        var openUntilMs = 0L
    }

    private val breakers = ConcurrentHashMap<String, BreakerState>()

    suspend fun analyze(request: AiRequest, nowMs: Long = System.currentTimeMillis()): AiResult {
        val diagnostics = mutableListOf<String>()
        providers.forEach { provider ->
            val breaker = breakers.getOrPut(provider.id) { BreakerState() }
            if (nowMs < breaker.openUntilMs) {
                diagnostics += "[${provider.id}] skipped — cooling down after repeated failures"
                return@forEach
            }
            when (val attempt = provider.analyze(request)) {
                is AiAttempt.Success -> {
                    resetBreaker(provider.id)
                    return AiResult.ProviderSuccess(
                        providerId = provider.id,
                        text = attempt.text,
                        analysisData = attempt.analysisData,
                    )
                }
                is AiAttempt.Failure -> {
                    diagnostics += attempt.message
                    // Local network errors must never bench the cloud tier.
                    if (attempt.breakerEligible) {
                        noteFailure(provider.id, nowMs, diagnostics)
                    }
                }
            }
        }
        return AiResult.DeterministicFallback(
            text = DeterministicFallbackBuilder.build(request),
            diagnostics = diagnostics,
        )
    }

    /** True while the provider's breaker is open (status banner surface later). */
    fun isBenched(providerId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val breaker = breakers[providerId] ?: return false
        return nowMs < breaker.openUntilMs
    }

    /**
     * Clears every breaker immediately. Called automatically after a successful
     * `/health` probe so a recovered backend is retried without waiting out a
     * stale cooldown.
     */
    fun resetCircuitBreaker() {
        breakers.values.forEach { breaker ->
            breaker.consecutiveFailures = 0
            breaker.openUntilMs = 0
        }
    }

    private fun resetBreaker(providerId: String) {
        breakers[providerId]?.let { breaker ->
            breaker.consecutiveFailures = 0
            breaker.openUntilMs = 0
        }
    }

    private fun noteFailure(
        providerId: String,
        nowMs: Long,
        diagnostics: MutableList<String>,
    ) {
        val breaker = breakers.getOrPut(providerId) { BreakerState() }
        breaker.consecutiveFailures += 1
        if (breaker.consecutiveFailures >= failureThreshold) {
            val cooldownMs = benchMs.coerceAtMost(MAX_COOLDOWN_MS)
            breaker.openUntilMs = nowMs + cooldownMs
            breaker.consecutiveFailures = 0
            diagnostics += "[$providerId] benched for ${cooldownMs / 1000}s " +
                "after $failureThreshold consecutive failures"
        }
    }

    private companion object {
        const val DEFAULT_FAILURE_THRESHOLD = 3
        const val DEFAULT_BENCH_MS = 5 * 60 * 1000L

        /** Field finding: a weak signal must never pause cloud AI for minutes. */
        const val MAX_COOLDOWN_MS = 15_000L
    }
}
