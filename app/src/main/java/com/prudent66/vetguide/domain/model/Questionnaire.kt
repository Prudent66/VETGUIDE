package com.prudent66.vetguide.domain.model

/**
 * One questionnaire step. Options map to signs ([QuestionnaireOption.symptomId])
 * that feed the diagnostic engine; options may also be plain "No/Normal" answers.
 */
data class QuestionnaireQuestion(
    val id: String,
    val text: String,
    val options: List<QuestionnaireOption>,
)

data class QuestionnaireOption(
    val id: String,
    val label: String,
    /** Symptom id contributed to the engine, or null for a plain "No" answer. */
    val symptomId: String?,
)
