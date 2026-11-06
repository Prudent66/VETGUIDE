package com.prudent66.vetguide.data.kb

import com.prudent66.vetguide.database.AppSettingEntity
import com.prudent66.vetguide.database.ConditionWeightEntity
import com.prudent66.vetguide.database.QuestionEntity
import com.prudent66.vetguide.database.QuestionOptionEntity
import com.prudent66.vetguide.database.QuestionnaireDao

object QuestionnaireSeedMarker {
    const val SEED_VERSION_KEY = "questionnaire_seed_version"
}

/**
 * Idempotent questionnaire application (roadmap Phase 3 acceptance). Pure JVM —
 * testable with an in-memory fake of [QuestionnaireDao]. Re-applying the same
 * payload produces the same database state (upsert semantics).
 */
object QuestionnaireSeedApplier {

    suspend fun apply(payload: QuestionnairePayload, dao: QuestionnaireDao) {
        dao.upsertQuestions(
            payload.questions.map { seed ->
                QuestionEntity(
                    id = seed.id,
                    species = seed.species,
                    text = seed.text,
                    orderIndex = seed.orderIndex,
                )
            },
        )
        dao.upsertQuestionOptions(
            payload.questions
                .flatMap { question -> question.options.map { it to question.id } }
                .map { (option, questionId) ->
                    QuestionOptionEntity(
                        id = option.id,
                        questionId = questionId,
                        label = option.label,
                        symptomId = option.symptomId,
                        orderIndex = option.orderIndex,
                    )
                },
        )
        dao.upsertConditionWeights(
            payload.weights.map { seed ->
                ConditionWeightEntity(
                    conditionId = seed.conditionId,
                    symptomId = seed.symptomId,
                    weight = seed.weight,
                )
            },
        )
        dao.upsertSetting(
            AppSettingEntity(
                key = QuestionnaireSeedMarker.SEED_VERSION_KEY,
                value = payload.version.toString(),
            ),
        )
    }
}
