package com.prudent66.vetguide.domain.model

/**
 * The exact animal an assessment is about. The category is authoritative: a
 * Wild subject never carries a domestic [Species] (wild patients persist
 * [Species.OTHER] only as a legacy enum placeholder), and a Domestic subject
 * never carries a wildlife group or wild subtype. Used by photo-analysis
 * prompts, result labels, PDF metadata and the separated dosage surfaces so
 * the selected animal is named precisely instead of falling back to "Other".
 */
data class AnimalSubject(
    val category: AnimalCategory = AnimalCategory.DOMESTIC,
    val species: Species? = null,
    val wildlifeGroup: String? = null,
    val wildAnimal: String? = null,
) {

    /** Resolved wildlife group label, never the raw enum name. */
    val groupLabel: String?
        get() = if (category == AnimalCategory.WILD && !wildlifeGroup.isNullOrBlank()) {
            ClinicalSpeciesCatalog.wildlifeGroup(wildlifeGroup)?.label ?: wildlifeGroup
        } else {
            null
        }

    /** Exact animal shown to the vet ("African Wild Dog · Jackal / Fox / Wild Dog"). */
    val label: String
        get() = when (category) {
            AnimalCategory.DOMESTIC ->
                species?.let { ClinicalSpeciesCatalog.label(it) } ?: "Unspecified species"
            AnimalCategory.WILD ->
                listOfNotNull(wildAnimal, groupLabel).joinToString(" · ")
                    .ifBlank { "Wild animal" }
        }

    /** Sent to the analysis prompt so every differential stays on this animal. */
    val constraint: String
        get() = "Selected animal: $label (${category.label} pathway). " +
            "Restrict every finding, differential diagnosis and clinical description " +
            "to $label only — never diagnose or describe conditions of another species. " +
            (if (category == AnimalCategory.WILD && groupLabel != null) {
                "Wildlife group: $groupLabel. Apply wildlife biosafety precautions."
            } else {
                ""
            })

    companion object {

        fun domestic(species: Species): AnimalSubject = AnimalSubject(
            category = AnimalCategory.DOMESTIC,
            species = species,
        )

        fun wild(group: String?, wildAnimal: String? = null): AnimalSubject = AnimalSubject(
            category = AnimalCategory.WILD,
            wildlifeGroup = group,
            wildAnimal = wildAnimal?.takeIf { it.isNotBlank() },
        )

        /** Builds the exact subject from a persisted patient record. */
        fun fromPatient(patient: Patient): AnimalSubject = if (
            patient.animalCategory == AnimalCategory.WILD
        ) {
            wild(patient.wildlifeGroup, patient.breed)
        } else {
            domestic(patient.species)
        }
    }
}
