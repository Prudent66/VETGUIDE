package com.prudent66.vetguide.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Knowledge Base DAO: seed application (idempotent via @Upsert), search,
 * species filter, and aggregate detail query.
 */
@Dao
interface KbDao {

    @Upsert
    suspend fun upsertConditions(conditions: List<ConditionEntity>)

    @Upsert
    suspend fun upsertSymptoms(symptoms: List<SymptomEntity>)

    @Upsert
    suspend fun upsertReferences(references: List<ReferenceEntity>)

    @Upsert
    suspend fun upsertConditionSpecies(links: List<ConditionSpeciesJunction>)

    @Upsert
    suspend fun upsertConditionSymptoms(links: List<ConditionSymptomJunction>)

    @Upsert
    suspend fun upsertConditionReferences(links: List<ConditionReferenceJunction>)

    @Upsert
    suspend fun upsertSetting(setting: AppSettingEntity)

    @Query("SELECT COALESCE((SELECT value FROM app_settings WHERE `key` = :seedVersionKey), '')")
    suspend fun getSettingValue(seedVersionKey: String): String

    @Transaction
    @Query(
        """
        SELECT * FROM kb_conditions
        WHERE (:query = '' OR name LIKE '%' || :query || '%')
          AND (:species IS NULL
               OR id IN (SELECT conditionId FROM kb_condition_species WHERE species = :species))
        ORDER BY name ASC
        """,
    )
    fun observeConditions(query: String, species: String?): Flow<List<ConditionWithSpecies>>

    @Transaction
    @Query("SELECT * FROM kb_conditions WHERE id = :conditionId")
    fun observeConditionDetails(conditionId: String): Flow<ConditionWithRelations?>
}
