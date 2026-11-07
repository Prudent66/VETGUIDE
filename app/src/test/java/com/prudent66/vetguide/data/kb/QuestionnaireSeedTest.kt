package com.prudent66.vetguide.data.kb

import com.prudent66.vetguide.database.AppSettingEntity
import com.prudent66.vetguide.database.ConditionWeightEntity
import com.prudent66.vetguide.database.QuestionEntity
import com.prudent66.vetguide.database.QuestionOptionEntity
import com.prudent66.vetguide.database.QuestionnaireDao
import com.prudent66.vetguide.database.QuestionWithOptions
import com.prudent66.vetguide.database.ConditionWithWeights
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pure-JVM tests for the questionnaire seed pipeline (roadmap Phase 3). */
class QuestionnaireSeedTest {

    private val seedJson = """
    {
      "version": 1,
      "questions": [
        {
          "id": "q-dog-vomiting",
          "species": "DOG",
          "orderIndex": 0,
          "text": "Has the animal been vomiting?",
          "options": [
            {"id": "opt-dog-vomiting-yes", "label": "Yes", "symptomId": "vomiting", "orderIndex": 0},
            {"id": "opt-dog-vomiting-no", "label": "No", "symptomId": null, "orderIndex": 1}
          ]
        }
      ],
      "weights": [
        {"conditionId": "canine-parvovirus", "symptomId": "vomiting", "weight": 10.0}
      ]
    }
    """.trimIndent()

    @Test
    fun `parse produces a validated payload`() {
        val payload = QuestionnaireSeedParser.parse(seedJson)

        assertEquals(1, payload.version)
        assertEquals(1, payload.questions.size)
        assertEquals(1, payload.weights.size)
        assertEquals(10.0, payload.weights[0].weight, 0.0)
    }

    @Test
    fun `parse rejects non-positive placeholder weights`() {
        val bad = seedJson.replace("\"weight\": 10.0", "\"weight\": 0.0")
        assertThrows(IllegalArgumentException::class.java) { QuestionnaireSeedParser.parse(bad) }
    }

    @Test
    fun `apply is idempotent - reapplying yields identical store state`(): Unit = runBlocking {
        val store = InMemoryQuestionnaireDao()
        val payload = QuestionnaireSeedParser.parse(seedJson)

        QuestionnaireSeedApplier.apply(payload, store)
        val snapshot = store.snapshot()

        QuestionnaireSeedApplier.apply(payload, store)
        assertEquals(snapshot, store.snapshot())
    }

    @Test
    fun `apply records the questionnaire seed version marker`(): Unit = runBlocking {
        val store = InMemoryQuestionnaireDao()
        QuestionnaireSeedApplier.apply(QuestionnaireSeedParser.parse(seedJson), store)

        assertEquals("1", store.settings[QuestionnaireSeedMarker.SEED_VERSION_KEY])
    }

    @Test
    fun `applied store contains normalized entities and weights`(): Unit = runBlocking {
        val store = InMemoryQuestionnaireDao()
        QuestionnaireSeedApplier.apply(QuestionnaireSeedParser.parse(seedJson), store)

        assertEquals(1, store.questions.size)
        assertEquals(2, store.options.size)
        assertTrue(store.options.any { it.symptomId == "vomiting" })
        assertTrue(store.options.any { it.symptomId == null })
        assertEquals("vomiting", store.weights.single().symptomId)
    }
}

/** In-memory fake of [QuestionnaireDao] for JVM tests (queries return empty). */
private class InMemoryQuestionnaireDao : QuestionnaireDao {
    val questions = mutableMapOf<String, QuestionEntity>()
    val options = mutableListOf<QuestionOptionEntity>()
    val weights = mutableListOf<ConditionWeightEntity>()
    val settings = mutableMapOf<String, String>()

    override suspend fun upsertQuestions(questions: List<QuestionEntity>) {
        this.questions.putAll(questions.associateBy { it.id })
    }

    override suspend fun upsertQuestionOptions(options: List<QuestionOptionEntity>) {
        this.options.removeAll(options.toSet())
        this.options.addAll(options)
    }

    override suspend fun upsertConditionWeights(weights: List<ConditionWeightEntity>) {
        this.weights.removeAll(weights.toSet())
        this.weights.addAll(weights)
    }

    override suspend fun upsertSetting(setting: AppSettingEntity) {
        settings[setting.key] = setting.value
    }

    override suspend fun getSettingValue(seedVersionKey: String): String =
        settings[seedVersionKey] ?: ""

    override suspend fun getQuestions(species: String): List<QuestionWithOptions> = emptyList()

    override suspend fun getConditionsWithWeights(): List<ConditionWithWeights> = emptyList()

    /** Content fingerprint for idempotency assertions. */
    fun snapshot(): String =
        questions.entries.sortedBy { it.key }.joinToString("|") {
            "${it.key}=${it.value.text}=${it.value.orderIndex}"
        } + ";" + options.sortedBy { it.id }.joinToString("|") {
            "${it.id}=${it.label}=${it.symptomId}"
        } + ";" + weights.sortedBy { "${it.conditionId}:${it.symptomId}" }.joinToString("|") {
            "${it.conditionId}:${it.symptomId}=${it.weight}"
        }
}
