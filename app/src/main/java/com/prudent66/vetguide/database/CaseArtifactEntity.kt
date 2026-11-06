package com.prudent66.vetguide.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.prudent66.vetguide.domain.model.CaseArtifactType

/**
 * Case artifacts (image/questionnaire/AI/SOAP — roadmap §11.1).
 * Stored chronologically to render the case timeline.
 */
@Entity(
    tableName = "case_artifacts",
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("caseId"), Index(value = ["caseId", "createdAtEpochMs"])],
)
data class CaseArtifactEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val type: CaseArtifactType,
    val content: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
