package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Knowledge Base — symptom/sign vocabulary. */
@Entity(tableName = "kb_symptoms")
data class SymptomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bodySystem: String,
    val updatedAtEpochMs: Long,
)
