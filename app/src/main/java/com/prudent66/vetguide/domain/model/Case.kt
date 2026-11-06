package com.prudent66.vetguide.domain.model

/** A consultation case belonging to exactly one patient. */
data class Case(
    val id: String,
    val patientId: String,
    val presentingComplaint: String,
    val status: CaseStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
