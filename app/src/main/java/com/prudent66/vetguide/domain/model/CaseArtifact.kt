package com.prudent66.vetguide.domain.model

/**
 * Investigation artifact attached to a case (image reference, questionnaire
 * snapshot, AI result, SOAP note). `content` holds text/JSON payloads for now;
 * binary image data lands in Phase 6.
 */
data class CaseArtifact(
    val id: String,
    val caseId: String,
    val type: CaseArtifactType,
    val content: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
