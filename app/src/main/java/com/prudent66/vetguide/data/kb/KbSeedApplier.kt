package com.prudent66.vetguide.data.kb

import com.prudent66.vetguide.database.AppSettingEntity
import com.prudent66.vetguide.database.ConditionEntity
import com.prudent66.vetguide.database.ConditionReferenceJunction
import com.prudent66.vetguide.database.ConditionSpeciesJunction
import com.prudent66.vetguide.database.ConditionSymptomJunction
import com.prudent66.vetguide.database.KbDao
import com.prudent66.vetguide.database.ReferenceEntity
import com.prudent66.vetguide.database.SymptomEntity

/** Marker constants kept separate so seeder and applier use one source. */
object KbSeedMarker {
    const val SEED_VERSION_KEY = "kb_seed_version"
}

/**
 * Idempotent seed application (roadmap Phase 2 acceptance: import idempotency).
 * Pure JVM — testable with an in-memory fake of [KbDao]. Re-applying the same
 * payload produces the same database state (upsert semantics).
 */
object KbSeedApplier {

    suspend fun apply(payload: KbSeedPayload, dao: KbDao) {
        val now = System.currentTimeMillis()
        dao.upsertConditions(
            payload.conditions.map { ref ->
                ConditionEntity(
                    id = ref.id,
                    name = ref.name,
                    overview = ref.overview,
                    urgency = ref.urgency,
                    updatedAtEpochMs = now,
                )
            },
        )
        dao.upsertSymptoms(
            payload.symptoms.map { ref ->
                SymptomEntity(
                    id = ref.id,
                    name = ref.name,
                    bodySystem = ref.bodySystem,
                    updatedAtEpochMs = now,
                )
            },
        )
        dao.upsertReferences(
            payload.references.map { ref ->
                ReferenceEntity(
                    id = ref.id,
                    title = ref.title,
                    note = ref.note,
                    updatedAtEpochMs = now,
                )
            },
        )
        dao.upsertConditionSpecies(
            payload.conditions
                .flatMap { ref -> ref.species.map { species -> ref.id to species } }
                .map { (conditionId, species) -> ConditionSpeciesJunction(conditionId, species) },
        )
        dao.upsertConditionSymptoms(
            payload.conditionSymptoms.entries
                .flatMap { (conditionId, ids) -> ids.map { symptomId -> conditionId to symptomId } }
                .map { (conditionId, symptomId) -> ConditionSymptomJunction(conditionId, symptomId) },
        )
        dao.upsertConditionReferences(
            payload.conditionReferences.entries
                .flatMap { (conditionId, ids) -> ids.map { referenceId -> conditionId to referenceId } }
                .map { (conditionId, referenceId) ->
                    ConditionReferenceJunction(conditionId, referenceId)
                },
        )
        dao.upsertSetting(
            AppSettingEntity(
                key = KbSeedMarker.SEED_VERSION_KEY,
                value = payload.version.toString(),
            ),
        )
    }
}
