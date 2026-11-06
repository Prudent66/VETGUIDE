package com.prudent66.vetguide.domain.model

/**
 * Patient record. Owner data is optional and minimized per roadmap §10.
 * Dates are epoch milliseconds (UTC). Age may be given as DOB or an
 * estimated value when DOB is unknown — never both null.
 */
data class Patient(
    val id: String,
    val name: String,
    val species: Species,
    val breed: String?,
    val sex: PatientSex,
    val neuterStatus: NeuterStatus,
    val dateOfBirthEpochMs: Long?,
    val estimatedAgeMonths: Int?,
    val weightKg: Double?,
    val weightUpdatedAtEpochMs: Long?,
    val ownerName: String?,
    val ownerPhone: String?,
    val ownerNotes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
