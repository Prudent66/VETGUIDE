package com.prudent66.vetguide.domain.model

/** Values collected from the Add Patient form; id & timestamps are assigned on save. */
data class PatientDraft(
    val name: String,
    val species: Species,
    val breed: String?,
    val sex: PatientSex,
    val ageMonths: Int?,
    val weightKg: Double?,
)
