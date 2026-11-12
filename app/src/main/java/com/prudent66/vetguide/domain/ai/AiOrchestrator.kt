package com.prudent66.vetguide.domain.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * Failover ladder over a priority provider list (roadmap §5.2): providers are
 * tried in order with max one attempt per provider per request. After
 * [failureThreshold] consecutive failures a provider is benched (circuits
 * open) for [benchMs], then becomes eligible again. When every provider is
 * exhausted or benched, the deterministic mode-4 fallback (questionnaire +
 * offline KB) answers — which is never an AiProvider (§5.1).
 *
 * Cloud-AI gate (Settings "Cloud AI Analysis", §5 execution mode A): while
 * [cloudAiEnabled] returns false no provider and no provider breaker state is
 * touched at all — analysis goes straight to the mode-4 deterministic
 * fallback, exactly like an offline environment. Until Phase 5 introduces the
 * on-device tier (mode 3), that gate covers every provider in the ladder.
 */
class AiOrchestrator(
    private val providers: List<AiProvider>,
    private val cloudAiEnabled: suspend () -> Boolean = { true },
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val benchMs: Long = DEFAULT_BENCH_MS,
) {

    private class BreakerState {
        var consecutiveFailures = 0
        var openUntilMs = 0L
    }

    private val breakers = ConcurrentHashMap<String, BreakerState>()

    suspend fun analyze(request: AiRequest, nowMs: Long = System.currentTimeMillis()): AiResult =
        if (!cloudAiEnabled()) {
            AiResult.DeterministicFallback(
                text = DeterministicFallbackBuilder.build(request),
                diagnostics = listOf(
                    "Cloud AI Analysis is OFF in Settings — providers were not consulted",
                ),
            )
        } else {
            runLadder(request, nowMs)
        }

    /** Provider ladder — only reached while the Settings cloud-AI gate is on. */
    private suspend fun runLadder(request: AiRequest, nowMs: Long): AiResult {
        val diagnostics = mutableListOf<String>()
        providers.forEach { provider ->
            val breaker = breakers.getOrPut(provider.id) { BreakerState() }
            if (nowMs < breaker.openUntilMs) {
                diagnostics += "[${provider.id}] skipped — cooling down after repeated failures"
                return@forEach
            }
            when (val attempt = provider.analyze(request)) {
                is AiAttempt.Success -> {
                    breaker.consecutiveFailures = 0
                    breaker.openUntilMs = 0
                    return AiResult.ProviderSuccess(
                        providerId = provider.id,
                        text = attempt.text,
                    )
                }
                is AiAttempt.Failure -> {
                    diagnostics += attempt.message
                    breaker.consecutiveFailures += 1
                    if (breaker.consecutiveFailures >= failureThreshold) {
                        breaker.openUntilMs = nowMs + benchMs
                        breaker.consecutiveFailures = 0
                        diagnostics += "[$provider.id] benched for ${benchMs / 1000}s " +
                            "after $failureThreshold consecutive failures"
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

    private companion object {
        /** Roadmap §5.2 defaults: 3 consecutive failures → benched 5 minutes. */
        const val DEFAULT_FAILURE_THRESHOLD = 3
        const val DEFAULT_BENCH_MS = 5 * 60 * 1000L
    }
}
