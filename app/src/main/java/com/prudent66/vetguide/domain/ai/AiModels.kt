package com.prudent66.vetguide.domain.ai

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

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
    /** Base64 image payload (§4.2); null for questionnaire-only requests. */
    val imageBase64: String? = null,
)

/** Outcome of ONE provider attempt — providers report failure explicitly. */
sealed interface AiAttempt {
    data class Success(
        val text: String,
        /**
         * Structured analysis (findings / conditions / next steps) when the
         * provider decoded a full AiAnalysisData payload; null for providers
         * that only produce text (on-device tier, fakes).
         */
        val analysisData: AiAnalysisData? = null,
    ) : AiAttempt

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
        /** Structured analysis for a fully decoded cloud payload (§10 UI). */
        val analysisData: AiAnalysisData? = null,
    ) : AiResult

    /** Deterministic questionnaire + offline-Knowledge Base fallback output. */
    data class DeterministicFallback(
        val text: String,
        /**
         * Raw provider-failure reasons (exception types, HTTP statuses,
         * bench states) for support/debugging surfaces. Deliberately NOT
         * part of [text]: the §10 rule-based narrative stays clean of any
         * provider provenance.
         */
        val diagnostics: List<String> = emptyList(),
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

/** Body accepted by the secure backend's /analyze endpoint (§8). */
@Serializable
data class AiAnalysisRequest(
    /** Base64 image payload; null for questionnaire-only requests. */
    val imageBase64: String? = null,
    /** Structured findings text from the questionnaire and/or case notes. */
    val prompt: String,
)

/** One suspected condition with the model's confidence estimate (§10). */
@Serializable
data class SuspectedCondition(
    val name: String,
    val confidence: Double,
)

/**
 * Structured analysis contract shared by the secure backend
 * (backend/src/index.ts) and cloud providers. Parsed defensively
 * (unknown keys ignored); empty/invalid results map to AiAttempt.Failure
 * (§10: "insufficient information", never a guess).
 */
@Serializable
data class AiAnalysisData(
    val provider: String,
    val model: String,
    val findings: List<String>,
    val suspectedConditions: List<SuspectedCondition>,
    val recommendedNextSteps: List<String> = emptyList(),
)

/**
 * Human-readable narrative for an AiAttempt.Success (§10): always carries
 * provider/model provenance and the standing "not a diagnosis" framing.
 */
fun AiAnalysisData.toNarrativeText(): String = buildString {
    append("[AI prediction — $provider/$model] ")
    append("Findings: ").append(findings.joinToString("; ")).append(". ")
    append("Suspected conditions (decision support, not a diagnosis): ")
    append(
        suspectedConditions.joinToString("; ") { condition ->
            "${condition.name} (confidence ${(condition.confidence * 100).roundToInt()}%)"
        },
    ).append(". ")
    if (recommendedNextSteps.isNotEmpty()) {
        append("Recommended next steps: ")
        append(recommendedNextSteps.joinToString("; ")).append(". ")
    }
    append(
        "Confidence values are model estimates, not validated diagnostic " +
            "probabilities — requires veterinary review.",
    )
}
