package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Knowledge Base — citation list. v1 seed contains only placeholder
 * reference entries requiring veterinary review (no invented citations).
 */
@Entity(tableName = "kb_references")
data class ReferenceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String,
    val updatedAtEpochMs: Long,
)
