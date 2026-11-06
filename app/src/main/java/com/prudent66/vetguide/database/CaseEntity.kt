package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.prudent66.vetguide.domain.model.CaseStatus

/**
 * Consultation cases table (roadmap §11.1). Deleting a patient removes all
 * their cases (privacy: hard delete per §10).
 */
@Entity(
    tableName = "cases",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("patientId"), Index("updatedAt")],
)
data class CaseEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val presentingComplaint: String,
    val status: CaseStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
