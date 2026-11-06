package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Knowledge Base — conditions table (roadmap §11.1). Seeded from
 * app/src/main/assets/kb_seed.json by the idempotent importer.
 * Overview text is placeholder until verified content lands.
 */
@Entity(tableName = "kb_conditions")
data class ConditionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val overview: String,
    val urgency: String?,
    val updatedAtEpochMs: Long,
)
