package com.prudent66.vetguide.domain.model

/**
 * Patient record. Owner data is optional and minimized per roadmap §10.
 * Dates are epoch milliseconds (UTC). Age is stored as a value + unit so
 * poultry batches ("3 Days") and young animals are as expressible as adults.
 */
data class Patient(
    val id: String,
    val name: String,
    /** Domestic / wildlife pathway; defaults for legacy records. */
    val animalCategory: AnimalCategory = AnimalCategory.DOMESTIC,
    val species: Species,
    val breed: String?,
    val sex: PatientSex,
    val neuterStatus: NeuterStatus,
    val dateOfBirthEpochMs: Long?,
    val ageValue: Double?,
    val ageUnit: AgeUnit?,
    val weightKg: Double?,
    val weightUpdatedAtEpochMs: Long?,
    val ownerName: String?,
    val ownerPhone: String?,
    val ownerNotes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
