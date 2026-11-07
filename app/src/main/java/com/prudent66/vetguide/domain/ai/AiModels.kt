package com.prudent66.vetguide.domain.ai

/**
 * Abstract request into the AI layer (roadmap §5–§6): providers receive a
 * normalized prompt; provider-specific quirks are handled inside each
 * implementation. Never contains secrets.
 */
data class AiRequest(
    /** One-line clinical context (species / breed / age / sex). */
    val headline: String,
    /** Structured findings text from the questionnaire and/or case notes. */
    val findings: String,
)

/** Outcome of ONE provider attempt — providers report failure explicitly. */
sealed interface AiAttempt {
    data class Success(val text: String) : AiAttempt

    data class Failure(val message: String) : AiAttempt
}

/**
 * Final orchestrator outcome with provenance. Provider results keep AI
 * provenance; the deterministic fallback (roadmap §5.1 mode 4) never does —
 * it is not an AI tier and must always be labeled "rule-based results" (§10).
 */
sealed interface AiResult {
    val providerId: String

    data class ProviderSuccess(
        override val providerId: String,
        val text: String,
        /** True for degraded tiers (e.g. on-device models, §5.1 mode 3). */
        val degraded: Boolean = false,
    ) : AiResult

    /** Deterministic questionnaire + offline-Knowledge Base fallback output. */
    data class DeterministicFallback(
        val text: String,
    ) : AiResult {
        override val providerId: String = OFFLINE_PROVIDER_ID
    }
}

/** Stable id for mode-4 deterministic output (never a provider id). */
const val OFFLINE_PROVIDER_ID = "deterministic-offline"

/**
 * Provider contract (§5/§6): one implementation per vendor/endpoint. Adding a
 * new provider must not require changes to the orchestrator or UI.
 * Implementations must not throw for an expected refusal — return
 * [AiAttempt.Failure] so the failover ladder can proceed.
 */
interface AiProvider {
    /** Stable identifier used for provenance and circuit-breaker bookkeeping. */
    val id: String

    suspend fun analyze(request: AiRequest): AiAttempt
}
