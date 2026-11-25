package com.prudent66.vetguide.domain.ai

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    /**
     * Optional vet-provided clinical context (e.g. "Vomiting for 2 days").
     * Injected into the backend prompt so the model prioritizes findings
     * aligned with the vet's own observation — while remaining objective.
     */
    val clinicalContext: String? = null,
    /** "Domestic" or "Wild" — the selected clinical pathway. */
    val animalCategory: String? = null,
    /** Exact selected animal ("African Wild Dog · Jackal / Fox / Wild Dog"). */
    val animalLabel: String? = null,
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

    data class Failure(
        val message: String,
        /**
         * True only for upstream HTTP failures (429/502/503/504) that may
         * trip the orchestrator's circuit breaker. Local network/DNS/timeout
         * errors stay false so a weak mobile signal never pauses cloud AI.
         */
        val breakerEligible: Boolean = false,
    ) : AiAttempt
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
    /** Optional vet-provided clinical context forwarded to the backend prompt. */
    val clinicalContext: String? = null,
    /** Selected pathway ("Domestic"/"Wild") forwarded as an explicit field. */
    val animalCategory: String? = null,
    /** Exact selected animal forwarded as an explicit field. */
    val animalLabel: String? = null,
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
    /**
     * Drug/injection prescriptions with dosages, kept separate from the
     * non-pharmacological [recommendedNextSteps] so the UI and SOAP/PDF
     * exports can present them in a dedicated prescription sheet. Defaults
     * to empty so responses from older backends still decode.
     */
    val prescriptions: List<String> = emptyList(),
) {
    /** Serializes the full structured analysis for persistence as a CaseArtifact. */
    fun toJson(): String = Json { ignoreUnknownKeys = true }.encodeToString(this)
}

/**
 * Trims trailing periods/semicolons (and any whitespace before them) from an
 * AI finding or next step. Model output regularly ends items with a full stop,
 * and the joins below add their own separator — without this the saved report
 * shows artifacts such as "earlobe.;", "(fowl pox).." or "guidelines..".
 */
fun sanitizeAiItem(item: String): String =
    item.trim().trimEnd { it == '.' || it == ';' || it.isWhitespace() }

/**
 * Human-readable narrative for an AiAttempt.Success (§10): always carries
 * provider/model provenance and the standing "not a diagnosis" framing.
 */
fun AiAnalysisData.toNarrativeText(): String = buildString {
    append("[Cloud analysis — $provider/$model] ")
    append("Findings: ")
    append(findings.joinToString("; ") { sanitizeAiItem(it) })
    append(". ")
    append("Suspected conditions (decision support, not a diagnosis): ")
    append(
        suspectedConditions.joinToString("; ") { condition ->
            "${sanitizeAiItem(condition.name)} " +
                "(confidence ${(condition.confidence * 100).roundToInt()}%)"
        },
    ).append(". ")
    if (prescriptions.isNotEmpty()) {
        append("Prescriptions & dosages: ")
        append(prescriptions.joinToString("; ") { sanitizeAiItem(it) }).append(". ")
    }
    if (recommendedNextSteps.isNotEmpty()) {
        append("Recommended next steps: ")
        append(recommendedNextSteps.joinToString("; ") { sanitizeAiItem(it) }).append(". ")
    }
    append(
        "Confidence values are model estimates, not validated diagnostic " +
            "probabilities — requires veterinary review.",
    )
}
