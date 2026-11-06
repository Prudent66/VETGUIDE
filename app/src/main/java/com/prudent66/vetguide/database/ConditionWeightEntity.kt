package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.Index

/**
 * DiagnosticEngine scoring input: condition ↔ symptom with a weight value
 * (roadmap Phase 3). Weights are DUMMY placeholder numbers — never clinical.
 */
@Entity(
    tableName = "kb_condition_weights",
    primaryKeys = ["conditionId", "symptomId"],
    indices = [Index("symptomId")],
)
data class ConditionWeightEntity(
    val conditionId: String,
    val symptomId: String,
    val weight: Double,
)
