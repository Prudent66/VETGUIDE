package com.prudent66.vetguide.domain.model

/** Values collected from the Add Patient form; id & timestamps are assigned on save. */
data class PatientDraft(
    val name: String,
    val animalCategory: AnimalCategory = AnimalCategory.DOMESTIC,
    val wildlifeGroup: String? = null,
    val species: Species,
    val breed: String?,
    val sex: PatientSex,
    val ageValue: Double?,
    val ageUnit: AgeUnit?,
    val weightKg: Double?,
)
