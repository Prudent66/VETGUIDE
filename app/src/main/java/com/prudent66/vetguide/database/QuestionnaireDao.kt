package com.prudent66.vetguide.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** Guided questionnaire DAO: seed application + on-demand loads for the flow. */
@Dao
interface QuestionnaireDao {

    @Upsert
    suspend fun upsertQuestions(questions: List<QuestionEntity>)

    @Upsert
    suspend fun upsertQuestionOptions(options: List<QuestionOptionEntity>)

    @Upsert
    suspend fun upsertConditionWeights(weights: List<ConditionWeightEntity>)

    @Upsert
    suspend fun upsertSetting(setting: AppSettingEntity)

    @Query("SELECT COALESCE((SELECT value FROM app_settings WHERE `key` = :seedVersionKey), '')")
    suspend fun getSettingValue(seedVersionKey: String): String

    @Transaction
    @Query("SELECT * FROM kb_questions WHERE species = :species ORDER BY orderIndex ASC")
    suspend fun getQuestions(species: String): List<QuestionWithOptions>

    @Transaction
    @Query("SELECT * FROM kb_conditions")
    suspend fun getConditionsWithWeights(): List<ConditionWithWeights>
}
