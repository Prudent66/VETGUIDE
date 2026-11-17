package com.prudent66.vetguide.domain.model

import com.prudent66.vetguide.domain.ai.AiAnalysisData
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializable payloads persisted as case_artifacts.content (roadmap §11.1,
 * Phase 7). Keeping them as JSON keeps the artifact table schema-stable while
 * the case detail editor and the PDF renderer decode typed structures.
 */
@Serializable
data class SoapPayload(
    val subjective: String = "",
    val objective: String = "",
    val assessment: String = "",
    val plan: String = "",
)

/** AI-result artifact: narrative + provenance + structured cloud payload. */
@Serializable
data class AiArtifactPayload(
    val narrative: String,
    val providerId: String,
    val degraded: Boolean = false,
    val analysisData: AiAnalysisData? = null,
)

/** One answered questionnaire step for the timeline / Subjective prefill. */
@Serializable
data class AnsweredQuestionPayload(
    val question: String,
    val answers: List<String>,
)

/** Questionnaire artifact: species + answered steps in display order. */
@Serializable
data class QuestionnaireArtifactPayload(
    val species: String? = null,
    val answers: List<AnsweredQuestionPayload> = emptyList(),
)

/** JSON codec for artifact payloads; malformed/legacy content reads as null. */
object CasePayloadCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> decodeOrNull(raw: String?): T? =
        raw?.let { content -> runCatching { json.decodeFromString<T>(content) }.getOrNull() }
}

/** Subjective (history) seed: the vet's context and/or questionnaire answers. */
fun renderSubjectiveSeed(
    clinicalContext: String?,
    answers: List<AnsweredQuestionPayload>,
): String = buildString {
    if (!clinicalContext.isNullOrBlank()) {
        append(clinicalContext.trim())
    }
    if (answers.isNotEmpty()) {
        if (isNotEmpty()) append("\n\n")
        answers.forEach { answer ->
            append(answer.question).append(": ")
            append(answer.answers.joinToString(", "))
            append("\n")
        }
    }
}.trim()

/** Assessment seed from the questionnaire's placeholder differentials. */
fun renderDifferentialsSeed(matches: List<DiagnosticMatch>): String = if (matches.isEmpty()) {
    "No ranked differentials for the selected signs (placeholder engine) — record " +
        "your own assessment."
} else {
    buildString {
        append("Ranked placeholder differentials (dummy weights — requires veterinary review): ")
        append(
            matches.joinToString("; ") { match ->
                "${match.conditionName} (${match.matchStrengthPercent}% match)"
            },
        )
    }
}

/** Human-readable species name for complaint/report lines ("Dog", "Goat", …). */
fun Species?.displayName(): String =
    this?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "unspecified species"

/** Assessment seed from structured AI output; null when there is none. */
fun renderSuspectedConditionsSeed(analysisData: AiAnalysisData?): String? =
    analysisData?.suspectedConditions
        ?.takeIf { it.isNotEmpty() }
        ?.let { conditions ->
            buildString {
                append("Model-suspected conditions (decision support, not a diagnosis): ")
                append(
                    conditions.joinToString("; ") { condition ->
                        "${condition.name} " +
                            "(confidence ${(condition.confidence * 100).roundToInt()}%)"
                    },
                )
            }
        }
