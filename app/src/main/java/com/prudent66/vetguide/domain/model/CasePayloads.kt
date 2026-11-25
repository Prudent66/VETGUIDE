package com.prudent66.vetguide.domain.model

import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.buildPrescriptionPlan
import com.prudent66.vetguide.domain.ai.sanitizeAiItem
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
    "No ranked differentials for the selected signs — record " +
        "your own assessment."
} else {
    buildString {
        append("Ranked differentials (seed weights — requires veterinary review): ")
        append(
            matches.joinToString("; ") { match ->
                "${match.conditionName} (${match.matchStrengthPercent}% match)"
            },
        )
    }
}

/** Strips the "[Cloud analysis — provider/model]" provenance tag for clinical views. */
private val AI_TAG_REGEX = Regex("""^\[(?:AI prediction|Cloud analysis)[^\]]*\]\s*""")

/** Collapses repeated sentence punctuation ("..", ".;", ". .") to the first mark. */
private val REPEATED_PUNCTUATION_REGEX = Regex("""([.;])\s*[.;]+""")

/**
 * Narrative text for the case record and PDF: the vet-facing clinical finding
 * report carries no provider/model banner, so legacy payloads that were saved
 * with the tag are cleaned on read as well. Repeated punctuation from older
 * saved payloads is collapsed so pre-existing cases also read cleanly.
 */
fun clinicalAnalysisText(narrative: String): String =
    narrative
        .replace(AI_TAG_REGEX, "")
        .replace(REPEATED_PUNCTUATION_REGEX, "$1")
        .trim()

/** Human-readable species name for complaint/report lines ("Dog", "Goat", …). */
fun Species?.displayName(): String =
    this?.let(ClinicalSpeciesCatalog::label) ?: "unspecified species"

/** Assessment seed from structured AI output; null when there is none. */
fun renderSuspectedConditionsSeed(analysisData: AiAnalysisData?): String? =
    analysisData?.suspectedConditions
        ?.takeIf { it.isNotEmpty() }
        ?.let { conditions ->
            buildString {
                append("Possible conditions (decision support, not a diagnosis):")
                conditions.forEach { condition ->
                    append("\n- ")
                    append(sanitizeAiItem(condition.name))
                    append(" (confidence ")
                    append((condition.confidence * 100).roundToInt())
                    append("%)")
                }
            }
        }

/**
 * Plan seed: prescriptions with dosages first, then the assessment's
 * non-pharmacological supportive care as a numbered list, matching the
 * Objective/Assessment pre-fill. Keeping both blocks in the persisted SOAP
 * plan is what preserves every prescription detail in the PDF export. Null
 * when the tier returned no structured plan (offline or degraded response).
 */
fun renderPlanSeed(
    analysisData: AiAnalysisData?,
    narrative: String? = null,
): String? {
    val plan = buildPrescriptionPlan(analysisData, narrative)
    if (!plan.hasPrescriptions && plan.supportiveCare.isEmpty()) return null
    return buildString {
        if (plan.hasPrescriptions) {
            append("Prescriptions & dosages:\n")
            plan.prescriptions.forEachIndexed { index, item ->
                append("${index + 1}. $item.\n")
            }
        }
        if (plan.supportiveCare.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("Supportive care & next steps:\n")
            plan.supportiveCare.forEachIndexed { index, item ->
                append("${index + 1}. $item.\n")
            }
        }
    }.trim()
}
