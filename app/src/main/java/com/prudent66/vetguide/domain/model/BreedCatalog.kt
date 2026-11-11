package com.prudent66.vetguide.domain.model

/**
 * Species-scoped breed lists for the patient register (Phase 6 UX pass).
 * Nigeria-focused common breeds; "Local Mixed" entries keep the form honest
 * for the dominant non-pedigree population. Domain-level so screens and
 * future import flows share one source of truth.
 */
object BreedCatalog {

    private val breedsBySpecies: Map<Species, List<String>> = mapOf(
        Species.DOG to listOf("Labrador", "German Shepherd", "Boerboel", "Local Mixed"),
        Species.CAT to listOf("Domestic Shorthair", "Persian", "Siamese", "Local Mixed"),
        Species.CATTLE to listOf("White Fulani", "Sokoto Gudali", "Red Bororo"),
        Species.GOAT to listOf("West African Dwarf", "Sahelian", "Red Sokoto"),
        Species.SHEEP to listOf("Yankasa", "Balami", "Uda"),
        Species.POULTRY to listOf("Broiler", "Layer", "Cockerel", "Local Chicken"),
        Species.PIG to listOf("Large White", "Landrace", "Local Breed"),
        Species.EQUINE to listOf("Local Pony", "Arabian", "Thoroughbred"),
        Species.OTHER to listOf("Local / Unknown"),
    )

    /** Breeds offered for the species (never empty — safe for dropdowns). */
    fun forSpecies(species: Species): List<String> = breedsBySpecies.getValue(species)
}
