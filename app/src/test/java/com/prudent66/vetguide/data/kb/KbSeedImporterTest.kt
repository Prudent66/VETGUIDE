package com.prudent66.vetguide.data.kb

import com.prudent66.vetguide.database.AppSettingEntity
import com.prudent66.vetguide.database.ConditionEntity
import com.prudent66.vetguide.database.ConditionReferenceJunction
import com.prudent66.vetguide.database.ConditionSpeciesJunction
import com.prudent66.vetguide.database.ConditionSymptomJunction
import com.prudent66.vetguide.database.ConditionWithRelations
import com.prudent66.vetguide.database.ConditionWithSpecies
import com.prudent66.vetguide.database.KbDao
import com.prudent66.vetguide.database.ReferenceEntity
import com.prudent66.vetguide.database.SymptomEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the KB seed pipeline: parsing, payload validation, and
 * idempotent application (roadmap Phase 2 acceptance).
 */
class KbSeedImporterTest {

    private val seedJson = """
    {
      "version": 1,
      "conditions": [
        {
          "id": "canine-parvovirus",
          "name": "Canine Parvovirus",
          "urgency": "EMERGENCY",
          "overview": "Placeholder overview — requires veterinary review.",
          "species": ["DOG"],
          "symptoms": ["lethargy", "vomiting"],
          "references": ["ref-placeholder-1"]
        },
        {
          "id": "peste-des-petits-ruminants",
          "name": "Peste des Petits Ruminants (PPR)",
          "urgency": "EMERGENCY",
          "overview": "Placeholder overview — requires veterinary review.",
          "species": ["GOAT", "SHEEP"],
          "symptoms": ["fever"],
          "references": ["ref-placeholder-1"]
        }
      ],
      "symptoms": [
        {"id": "lethargy", "name": "Lethargy", "bodySystem": "General"},
        {"id": "vomiting", "name": "Vomiting", "bodySystem": "Gastrointestinal"},
        {"id": "fever", "name": "Fever", "bodySystem": "General"}
      ],
      "references": [
        {"id": "ref-placeholder-1", "title": "Placeholder reference — requires veterinary review", "note": "Replace with verified sources."}
      ]
    }
    """.trimIndent()

    @Test
    fun `parse produces normalized payload with junction maps`() {
        val payload = KbSeedParser.parse(seedJson)

        assertEquals(1, payload.version)
        assertEquals(2, payload.conditions.size)
        assertEquals(3, payload.symptoms.size)
        assertEquals(1, payload.references.size)
        assertEquals(listOf("lethargy", "vomiting"), payload.conditionSymptoms["canine-parvovirus"])
        assertEquals(listOf("GOAT", "SHEEP"), payload.conditions[1].species)
    }

    @Test
    fun `parse rejects unknown references inside a condition`() {
        val bad = seedJson.replaceFirst("\n\"ref-placeholder-1\"", "\n\"ref-unknown\"")
        assertThrows(IllegalArgumentException::class.java) { KbSeedParser.parse(bad) }
    }

    @Test
    fun `apply is idempotent - reapplying yields identical store state`(): Unit = runBlocking {
        val store = InMemoryKbDao()
        val payload = KbSeedParser.parse(seedJson)

        KbSeedApplier.apply(payload, store)
        val snapshot = store.snapshot()

        // Re-import (idempotency): state must be identical, not duplicated.
        KbSeedApplier.apply(payload, store)
        assertEquals(snapshot, store.snapshot())
    }

    @Test
    fun `apply records the seed version marker`(): Unit = runBlocking {
        val store = InMemoryKbDao()
        KbSeedApplier.apply(KbSeedParser.parse(seedJson), store)

        assertEquals("1", store.settings[KbSeedMarker.SEED_VERSION_KEY])
    }

    @Test
    fun `applied store contains normalized entities`(): Unit = runBlocking {
        val store = InMemoryKbDao()
        KbSeedApplier.apply(KbSeedParser.parse(seedJson), store)

        assertEquals(2, store.conditions.size)
        val ppr = store.conditions["peste-des-petits-ruminants"]
        assertEquals("Peste des Petits Ruminants (PPR)", ppr?.name)
        assertEquals("EMERGENCY", ppr?.urgency)
        assertTrue(ppr?.overview?.contains("requires veterinary review") == true)
        // junctions
        assertTrue(store.speciesTags.any { it.conditionId == "peste-des-petits-ruminants" && it.species == "SHEEP" })
        assertEquals(3, store.conditionSymptoms.size)
    }
}

/** In-memory fake of [KbDao] for JVM tests (unused methods return empty flows). */
private class InMemoryKbDao : KbDao {
    val conditions = mutableMapOf<String, ConditionEntity>()
    val symptoms = mutableMapOf<String, SymptomEntity>()
    val references = mutableMapOf<String, ReferenceEntity>()
    val speciesTags = mutableListOf<ConditionSpeciesJunction>()
    val conditionSymptoms = mutableListOf<ConditionSymptomJunction>()
    val conditionReferences = mutableListOf<ConditionReferenceJunction>()
    val settings = mutableMapOf<String, String>()

    override suspend fun upsertConditions(conditions: List<ConditionEntity>) {
        this.conditions.putAll(conditions.associateBy { it.id })
    }

    override suspend fun upsertSymptoms(symptoms: List<SymptomEntity>) {
        this.symptoms.putAll(symptoms.associateBy { it.id })
    }

    override suspend fun upsertReferences(references: List<ReferenceEntity>) {
        this.references.putAll(references.associateBy { it.id })
    }

    override suspend fun upsertConditionSpecies(links: List<ConditionSpeciesJunction>) {
        speciesTags.removeAll(links.toSet())
        speciesTags.addAll(links)
    }

    override suspend fun upsertConditionSymptoms(links: List<ConditionSymptomJunction>) {
        conditionSymptoms.removeAll(links.toSet())
        conditionSymptoms.addAll(links)
    }

    override suspend fun upsertConditionReferences(links: List<ConditionReferenceJunction>) {
        conditionReferences.removeAll(links.toSet())
        conditionReferences.addAll(links)
    }

    override suspend fun upsertSetting(setting: AppSettingEntity) {
        settings[setting.key] = setting.value
    }

    override suspend fun getSettingValue(seedVersionKey: String): String =
        settings[seedVersionKey] ?: ""

    override fun observeConditions(query: String, species: String?): Flow<List<ConditionWithSpecies>> =
        emptyFlow()

    override fun observeConditionDetails(conditionId: String): Flow<ConditionWithRelations?> =
        emptyFlow()

    /** Content fingerprint for idempotency assertions (timestamps excluded). */
    fun snapshot(): String = buildString {
        append(conditions.toSortedMap().entries.joinToString("|") { k -> "${k.key}=${k.value.name}=${k.value.overview}" })
        append(";")
        append(symptoms.toSortedMap().entries.joinToString("|") { k -> "${k.key}=${k.value.name}" })
        append(";")
        append(references.toSortedMap().entries.joinToString("|") { k -> "${k.key}=${k.value.title}" })
        append(";")
        append(speciesTags.sortedBy { it.conditionId + it.species }.joinToString { "${it.conditionId}:${it.species}" })
        append(";")
        append(
            conditionSymptoms
                .sortedWith(compareBy({ it.conditionId }, { it.symptomId }))
                .joinToString { "${it.conditionId}:${it.symptomId}" },
        )
        append(";")
        append(
            conditionReferences
                .sortedWith(compareBy({ it.conditionId }, { it.referenceId }))
                .joinToString { "${it.conditionId}:${it.referenceId}" },
        )
        append(";")
        append(settings.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" })
    }
}
