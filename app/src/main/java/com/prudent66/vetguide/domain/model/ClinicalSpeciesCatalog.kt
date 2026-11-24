package com.prudent66.vetguide.domain.model

/**
 * Species options for the Domestic / Wild pathways. Domestic uses the eight
 * supported [Species] values; Wild uses the dedicated wildlife groups.
 */
object ClinicalSpeciesCatalog {

    val domesticLabels: List<String> = listOf(
        "Cattle", "Goat", "Sheep", "Poultry", "Pig", "Equine", "Dog", "Cat",
    )

    val wildlifeLabels: List<String> = WildlifeGroup.entries.map { it.label }

    fun labels(category: AnimalCategory): List<String> = when (category) {
        AnimalCategory.DOMESTIC -> domesticLabels
        AnimalCategory.WILD -> wildlifeLabels
    }

    fun first(category: AnimalCategory): String = labels(category).first()

    fun label(species: Species): String =
        domesticLabels.firstOrNull { domesticSpecies(it) == species }
            ?: species.name.lowercase().replaceFirstChar { it.uppercase() }

    fun domesticSpecies(label: String): Species? =
        Species.entries.firstOrNull { it.name.equals(label, ignoreCase = true) }

    fun wildlifeGroup(label: String?): WildlifeGroup? =
        WildlifeGroup.entries.firstOrNull { it.label.equals(label, ignoreCase = true) }

    /** Human-readable species for a patient record in lists and pickers. */
    fun display(patient: Patient): String = when {
        patient.animalCategory == AnimalCategory.WILD && !patient.wildlifeGroup.isNullOrBlank() ->
            patient.wildlifeGroup
        patient.species == Species.OTHER -> "Other"
        else -> patient.species.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
