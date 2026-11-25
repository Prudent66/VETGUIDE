package com.prudent66.vetguide.domain.model

/**
 * Species-scoped breed lists for the patient register and herd mode. Each
 * domestic species keeps its original local/Nigerian breeds and adds global
 * commercial breeds; "Crossbreed / Mixed" entries keep the form honest for
 * non-pedigree animals. Wild animals use their own subtype picker, never this
 * catalogue. Domain-level so screens and future import flows share one source.
 */
object BreedCatalog {

    private val mixed = "Crossbreed / Mixed"

    private val breedsBySpecies: Map<Species, List<String>> = mapOf(
        Species.CATTLE to listOf(
            "Holstein-Friesian", "Jersey", "Angus", "Hereford", "Charolais", "Simmental",
            "Limousin", "Brahman", "Zebu / Gir", "Brown Swiss",
            "White Fulani", "Sokoto Gudali", "Red Bororo", mixed,
        ),
        Species.GOAT to listOf(
            "Boer", "Saanen", "Alpine", "Anglo-Nubian", "Toggenburg", "Kalahari Red",
            "Jamnapari", "Oberhasli",
            "West African Dwarf", "Sahelian", "Red Sokoto", mixed,
        ),
        Species.SHEEP to listOf(
            "Merino", "Suffolk", "Dorper", "Texel", "Hampshire", "Romney", "Awassi",
            "Katahdin", "Corriedale",
            "Yankasa", "Balami", "Uda", mixed,
        ),
        Species.POULTRY to listOf(
            "Ross / Cobb Broiler", "Hy-Line / ISA Brown Layer", "Rhode Island Red",
            "Leghorn", "Plymouth Rock", "Australorp", "Turkey", "Duck", "Goose",
            "Guinea Fowl", "Quail",
            "Broiler", "Layer", "Cockerel", "Local Chicken", mixed,
        ),
        Species.PIG to listOf(
            "Duroc", "Hampshire", "Berkshire", "Pietrain", "Yorkshire", "Chester White",
            "Poland China",
            "Large White", "Landrace", "Local Breed", mixed,
        ),
        Species.EQUINE to listOf(
            "Quarter Horse", "Warmblood", "Friesian", "Clydesdale", "Mustang / Stock Horse",
            "Andalusian", "Appaloosa", "Donkey / Mule",
            "Arabian", "Thoroughbred", "Local Pony", mixed,
        ),
        Species.DOG to listOf(
            "Golden Retriever", "Bulldog", "French Bulldog", "Rottweiler", "Poodle",
            "Beagle", "Siberian Husky", "Dachshund", "Chihuahua", "Pit Bull / Terrier",
            "Boxer", "Doberman",
            "Labrador", "German Shepherd", "Boerboel", "Local Mixed", mixed,
        ),
        Species.CAT to listOf(
            "Maine Coon", "British Shorthair", "Ragdoll", "Bengal", "Sphynx",
            "Scottish Fold", "Abyssinian", "Russian Blue",
            "Domestic Shorthair", "Persian", "Siamese", "Local Mixed", mixed,
        ),
        Species.BUFFALO to listOf(
            "Murrah", "Nili-Ravi", "Mediterranean", "Swamp Buffalo",
            "African Domestic Buffalo", mixed,
        ),
        Species.CAMEL to listOf(
            "Dromedary (One-Humped)", "Bactrian (Two-Humped)", "Somali / East African",
            "Sahelian Dromedary", mixed,
        ),
        Species.RABBIT to listOf(
            "New Zealand White", "Californian", "Flemish Giant", "Rex", "Chinchilla",
            "Dutch", "Local / Mixed Rabbit",
        ),
        Species.FISH to listOf(
            "African Catfish (Clarias)", "Nile Tilapia", "Atlantic Salmon",
            "Rainbow Trout", "Common Carp", "Pangasius", "Ornamental / Koi",
        ),
        Species.OTHER to listOf("Local / Unknown", mixed),
    )

    /** Breeds offered for the domestic species (never empty — safe for dropdowns). */
    fun forSpecies(species: Species): List<String> = breedsBySpecies.getValue(species)
}
