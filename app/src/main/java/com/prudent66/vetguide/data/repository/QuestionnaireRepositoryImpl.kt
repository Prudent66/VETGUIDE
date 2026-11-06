package com.prudent66.vetguide.data.repository

import com.prudent66.vetguide.database.QuestionnaireDao
import com.prudent66.vetguide.domain.model.DiagnosticCandidate
import com.prudent66.vetguide.domain.model.KbUrgency
import com.prudent66.vetguide.domain.model.QuestionnaireOption
import com.prudent66.vetguide.domain.model.QuestionnaireQuestion
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.QuestionnaireRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionnaireRepositoryImpl @Inject constructor(
    private val questionnaireDao: QuestionnaireDao,
) : QuestionnaireRepository {

    override suspend fun loadQuestions(species: Species): List<QuestionnaireQuestion> =
        questionnaireDao.getQuestions(species.name).map { row ->
            QuestionnaireQuestion(
                id = row.question.id,
                text = row.question.text,
                options = row.options
                    .sortedBy { it.orderIndex }
                    .map { option ->
                        QuestionnaireOption(
                            id = option.id,
                            label = option.label,
                            symptomId = option.symptomId,
                        )
                    },
            )
        }

    override suspend fun loadCandidates(): List<DiagnosticCandidate> =
        questionnaireDao.getConditionsWithWeights()
            .filter { it.weights.isNotEmpty() }
            .map { row ->
                DiagnosticCandidate(
                    conditionId = row.condition.id,
                    conditionName = row.condition.name,
                    urgency = urgencyOf(row.condition.urgency),
                    symptomWeights = row.weights.associate { it.symptomId to it.weight },
                )
            }
}

private fun urgencyOf(raw: String?): KbUrgency? = raw
    ?.let { value -> KbUrgency.entries.firstOrNull { urgency -> urgency.name == value } }
