package com.prudent66.vetguide.data.kb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serialization models for the questionnaire seed (roadmap Phase 3). All
 * weights are DUMMY placeholder numbers — never clinical reality — and
 * question/option text is placeholder pending veterinary review.
 */
@Serializable
data class QuestionnaireSeedJson(
    val version: Int,
    val questions: List<SeedQuestion> = emptyList(),
    val weights: List<SeedWeight> = emptyList(),
)

@Serializable
data class SeedQuestion(
    val id: String,
    val species: String,
    val orderIndex: Int,
    val text: String,
    val options: List<SeedOption> = emptyList(),
)

@Serializable
data class SeedOption(
    val id: String,
    val label: String,
    val orderIndex: Int,
    val symptomId: String? = null,
)

@Serializable
data class SeedWeight(
    val conditionId: String,
    val symptomId: String,
    val weight: Double,
)

/** Validated payload ready for persistence. */
data class QuestionnairePayload(
    val version: Int,
    val questions: List<SeedQuestion>,
    val weights: List<SeedWeight>,
) {
    init {
        require(version >= 1) { "questionnaire seed version must be >= 1" }
        val questionIds = questions.map { it.id }
        require(questionIds.size == questionIds.distinct().size) {
            "duplicate question ids in questionnaire seed"
        }
        val optionIds = questions.flatMap { it.options.map { option -> option.id } }
        require(optionIds.size == optionIds.distinct().size) {
            "duplicate option ids in questionnaire seed"
        }
        require(weights.all { it.weight > 0.0 }) {
            "questionnaire weights must be positive placeholder values"
        }
        questions.forEach { question ->
            require(question.options.isNotEmpty()) {
                "question ${'$'}{question.id} has no options"
            }
        }
    }
}

object QuestionnaireSeedParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(seedJson: String): QuestionnairePayload {
        val seed = json.decodeFromString<QuestionnaireSeedJson>(seedJson)
        return QuestionnairePayload(
            version = seed.version,
            questions = seed.questions,
            weights = seed.weights,
        )
    }
}
