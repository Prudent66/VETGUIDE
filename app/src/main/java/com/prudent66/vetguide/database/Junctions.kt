package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.Index

/** Condition ↔ species junction (species stored as the [Species] enum name). */
@Entity(
    tableName = "kb_condition_species",
    primaryKeys = ["conditionId", "species"],
    indices = [Index("species")],
)
data class ConditionSpeciesJunction(
    val conditionId: String,
    val species: String,
)

/** Condition ↔ symptom junction. */
@Entity(
    tableName = "kb_condition_symptoms",
    primaryKeys = ["conditionId", "symptomId"],
    indices = [Index("symptomId")],
)
data class ConditionSymptomJunction(
    val conditionId: String,
    val symptomId: String,
)

/** Condition ↔ reference junction. */
@Entity(
    tableName = "kb_condition_references",
    primaryKeys = ["conditionId", "referenceId"],
    indices = [Index("referenceId")],
)
data class ConditionReferenceJunction(
    val conditionId: String,
    val referenceId: String,
)
