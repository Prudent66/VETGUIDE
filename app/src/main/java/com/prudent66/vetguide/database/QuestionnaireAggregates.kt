package com.prudent66.vetguide.database

import androidx.room.Embedded
import androidx.room.Relation

/** Questionnaire flow aggregate: question + ordered option rows. */
data class QuestionWithOptions(
    @Embedded val question: QuestionEntity,
    @Relation(parentColumn = "id", entityColumn = "questionId")
    val options: List<QuestionOptionEntity>,
)

/** Engine aggregate: condition + its dummy symptom weights. */
data class ConditionWithWeights(
    @Embedded val condition: ConditionEntity,
    @Relation(parentColumn = "id", entityColumn = "conditionId")
    val weights: List<ConditionWeightEntity>,
)
