package com.prudent66.vetguide.data.repository

import com.prudent66.vetguide.database.KbDao
import com.prudent66.vetguide.database.ConditionWithRelations
import com.prudent66.vetguide.database.ConditionWithSpecies
import com.prudent66.vetguide.domain.model.KbConditionDetail
import com.prudent66.vetguide.domain.model.KbConditionSummary
import com.prudent66.vetguide.domain.model.KbReference
import com.prudent66.vetguide.domain.model.KbUrgency
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.Symptom
import com.prudent66.vetguide.domain.repository.KnowledgeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val kbDao: KbDao,
) : KnowledgeRepository {

    override fun observeConditions(
        query: String,
        species: Species?,
    ): Flow<List<KbConditionSummary>> =
        kbDao.observeConditions(query = query, species = species?.name)
            .map { rows -> rows.map { it.toSummary() } }

    override fun observeConditionDetail(conditionId: String): Flow<KbConditionDetail?> =
        kbDao.observeConditionDetails(conditionId).map { it?.toDetail() }
}

private fun toSpeciesList(tags: List<String>): List<Species> = tags.mapNotNull { tag ->
    try {
        Species.valueOf(tag)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun urgencyOrNull(raw: String?): KbUrgency? =
    raw?.let { value -> try { KbUrgency.valueOf(value) } catch (e: IllegalArgumentException) { null } }

private fun ConditionWithSpecies.toSummary(): KbConditionSummary = KbConditionSummary(
    id = condition.id,
    name = condition.name,
    species = toSpeciesList(species.map { it.species }),
    urgency = urgencyOrNull(condition.urgency),
)

private fun ConditionWithRelations.toDetail(): KbConditionDetail = KbConditionDetail(
    id = condition.id,
    name = condition.name,
    species = toSpeciesList(species.map { it.species }),
    urgency = urgencyOrNull(condition.urgency),
    overview = condition.overview,
    symptoms = symptoms
        .map { Symptom(id = it.id, name = it.name, bodySystem = it.bodySystem) }
        .sortedBy { it.name },
    references = references
        .map { KbReference(id = it.id, title = it.title, note = it.note) }
        .sortedBy { it.title },
)
