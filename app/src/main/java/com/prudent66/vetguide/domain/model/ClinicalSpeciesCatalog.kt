package com.prudent66.vetguide.domain.model

/**
 * Species options for the Domestic / Wild pathways. Domestic uses the eight
 * supported [Species] values; Wild uses the dedicated wildlife groups plus a
 * concrete sub-species picker.
 */
object ClinicalSpeciesCatalog {

    val domesticLabels: List<String> = listOf(
        "Cattle", "Goat", "Sheep", "Poultry", "Pig", "Equine", "Dog", "Cat",
    )

    val wildlifeLabels: List<String> = WildlifeGroup.entries.map { it.label }

    private val wildAnimals: Map<WildlifeGroup, List<String>> = mapOf(
        WildlifeGroup.WILD_RUMINANTS to listOf(
            "Duiker", "Gazelle", "Impala", "Bushbuck", "Kob", "Kudu", "Roan Antelope",
            "African Buffalo", "Giraffe", "Other / Unidentified",
        ),
        WildlifeGroup.WILD_CANIDS to listOf(
            "Side-Striped Jackal", "Black-Backed Jackal", "African Wild Dog", "Pale Fox",
            "Spotted Hyena", "Civet / Genet", "Other / Unidentified",
        ),
        WildlifeGroup.WILD_FELIDS to listOf(
            "Lion", "Leopard", "Cheetah", "Serval", "Caracal", "African Wildcat",
            "Other / Unidentified",
        ),
        WildlifeGroup.PRIMATES to listOf(
            "Olive Baboon", "Vervet / Green Monkey", "Patas Monkey", "Mona Monkey",
            "Chimpanzee", "Drill / Mandrill", "Other / Unidentified",
        ),
        WildlifeGroup.WILD_BIRDS to listOf(
            "Eagle", "Vulture", "Hawk / Falcon", "Owl", "Ostrich", "Crown Crane",
            "Wild Waterfowl", "Other / Unidentified",
        ),
        WildlifeGroup.REPTILES to listOf(
            "Python / Viper / Cobra", "Nile Crocodile", "Tortoise", "Monitor Lizard",
            "Chameleon", "Other / Unidentified",
        ),
        WildlifeGroup.SMALL_WILD_MAMMALS to listOf(
            "Pangolin", "Grasscutter (Cane Rat)", "African Hare", "Fruit Bat", "Porcupine",
            "Bushbaby", "Other / Unidentified",
        ),
    )

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

    /**
     * Resolves an enum name (`WILD_RUMINANTS`), the current label, or the
     * pre-Build-21 label (`Wild Ruminants / Antelope`) to the same group.
     */
    fun wildlifeGroup(label: String?): WildlifeGroup? = WildlifeGroup.entries.firstOrNull {
        it.label.equals(label, ignoreCase = true) ||
            it.previousLabel.equals(label, ignoreCase = true) ||
            it.name.equals(label, ignoreCase = true)
    }

    /** Concrete wild animals for the secondary picker; never "Local / Unknown". */
    fun wildAnimalsFor(group: WildlifeGroup): List<String> =
        wildAnimals.getValue(group)

    /** Human-readable species for a patient record in lists and pickers. */
    fun display(patient: Patient): String = when {
        patient.animalCategory == AnimalCategory.WILD && !patient.wildlifeGroup.isNullOrBlank() ->
            wildlifeGroup(patient.wildlifeGroup)?.label ?: patient.wildlifeGroup
        patient.species == Species.OTHER -> "Other"
        else -> patient.species.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
