package com.prudent66.vetguide.data.kb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serialization models for the KB seed JSON (roadmap Phase 2 — schema in
 * app/src/main/assets/kb_seed.json). Contains NO clinical content: every
 * payload string is a placeholder requiring veterinary review.
 */
@Serializable
data class KbSeedJson(
    val version: Int,
    val conditions: List<ConditionRef> = emptyList(),
    val symptoms: List<SymptomRef> = emptyList(),
    val references: List<ReferenceRef> = emptyList(),
)

@Serializable
data class ConditionRef(
    val id: String,
    val name: String,
    val urgency: String? = null,
    val overview: String,
    val species: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val references: List<String> = emptyList(),
)

@Serializable
data class SymptomRef(
    val id: String,
    val name: String,
    val bodySystem: String = "General",
)

@Serializable
data class ReferenceRef(
    val id: String,
    val title: String,
    val note: String = "",
)

/** Normalized payload ready for persistence (parity of ids across tables). */
data class KbSeedPayload(
    val version: Int,
    val conditions: List<ConditionRef>,
    val symptoms: List<SymptomRef>,
    val references: List<ReferenceRef>,
    /** condition id -> symptom ids */
    val conditionSymptoms: Map<String, List<String>>,
    /** condition id -> reference ids */
    val conditionReferences: Map<String, List<String>>,
) {
    init {
        require(version >= 1) { "KB seed version must be >= 1" }
        val knownSymptoms = symptoms.map { it.id }.toSet()
        val knownReferences = references.map { it.id }.toSet()
        val unknown = conditionSymptoms.values.flatten() +
            conditionReferences.values.flatten()
        require(unknown.all { it in knownSymptoms || it in knownReferences }) {
            "KB seed references unknown symptom/reference ids"
        }
    }
}

object KbSeedParser {

    /** Unknown symptom/reference ids inside a condition make the whole seed invalid. */
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(seedJson: String): KbSeedPayload {
        val seed = json.decodeFromString<KbSeedJson>(seedJson)
        return KbSeedPayload(
            version = seed.version,
            conditions = seed.conditions,
            symptoms = seed.symptoms,
            references = seed.references,
            conditionSymptoms = seed.conditions.associate { it.id to it.symptoms },
            conditionReferences = seed.conditions.associate { it.id to it.references },
        )
    }
}
