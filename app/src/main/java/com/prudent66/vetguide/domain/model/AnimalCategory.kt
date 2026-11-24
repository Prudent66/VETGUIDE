package com.prudent66.vetguide.domain.model

/** Domestic versus wildlife clinical pathway. Persisted by name in Room. */
enum class AnimalCategory {
    DOMESTIC,
    WILD;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/** Wildlife species groups available when the Wild pathway is selected. */
enum class WildlifeGroup(val label: String) {
    WILD_RUMINANTS("Wild Ruminants / Antelope"),
    WILD_CANIDS("Wild Canids"),
    WILD_FELIDS("Wild Felids"),
    WILD_BIRDS("Wild Birds / Raptors"),
    PRIMATES("Primates"),
    REPTILES("Reptiles"),
    SMALL_WILD_MAMMALS("Small Wild Mammals"),
}

/**
 * Wildlife disease references with biosafety/restraint cautions. Decision
 * support only — never a substitute for veterinary or wildlife-authority care.
 */
data class WildlifeDisease(
    val id: String,
    val name: String,
    val groups: Set<WildlifeGroup>,
    val caution: String,
)

object WildlifeDiseaseCatalog {

    val diseases: List<WildlifeDisease> = listOf(
        WildlifeDisease(
            id = "rabies",
            name = "Rabies",
            groups = setOf(WildlifeGroup.WILD_CANIDS, WildlifeGroup.WILD_FELIDS, WildlifeGroup.PRIMATES, WildlifeGroup.SMALL_WILD_MAMMALS),
            caution = "Zoonotic and fatal — use PPE, avoid bite/overlay risk, notify public-health and wildlife authorities.",
        ),
        WildlifeDisease(
            id = "anthrax",
            name = "Anthrax",
            groups = setOf(WildlifeGroup.WILD_RUMINANTS, WildlifeGroup.PRIMATES, WildlifeGroup.SMALL_WILD_MAMMALS),
            caution = "Zoonotic spore-forming organism — do not open carcasses; notify authorities and use PPE.",
        ),
        WildlifeDisease(
            id = "capture-myopathy",
            name = "Capture Myopathy",
            groups = WildlifeGroup.entries.toSet(),
            caution = "Minimise chase time and restraint stress; allow quiet recovery and monitor for days after capture.",
        ),
        WildlifeDisease(
            id = "wildlife-avian-influenza",
            name = "Wildlife Avian Influenza / Newcastle",
            groups = setOf(WildlifeGroup.WILD_BIRDS),
            caution = "Highly contagious and zoonotic — isolate, use PPE, and report suspect mortality events.",
        ),
        WildlifeDisease(
            id = "wild-morbillivirus",
            name = "PPR / Rinderpest-like Morbillivirus",
            groups = setOf(WildlifeGroup.WILD_RUMINANTS),
            caution = "Highly contagious — isolate affected animals and notify livestock/wildlife authorities.",
        ),
        WildlifeDisease(
            id = "canine-distemper",
            name = "Canine Distemper in Wild Carnivores",
            groups = setOf(WildlifeGroup.WILD_CANIDS, WildlifeGroup.SMALL_WILD_MAMMALS),
            caution = "Highly contagious — isolate and avoid contact with domestic dogs and other carnivores.",
        ),
        WildlifeDisease(
            id = "sarcoptic-mange",
            name = "Sarcoptic Mange",
            groups = setOf(WildlifeGroup.WILD_CANIDS, WildlifeGroup.SMALL_WILD_MAMMALS, WildlifeGroup.WILD_RUMINANTS),
            caution = "Zoonotic and highly contagious — use gloves and dedicated equipment when handling.",
        ),
        WildlifeDisease(
            id = "wildlife-trypanosomiasis",
            name = "Wildlife Trypanosomiasis",
            groups = setOf(WildlifeGroup.WILD_RUMINANTS, WildlifeGroup.WILD_CANIDS, WildlifeGroup.PRIMATES),
            caution = "Vector-borne — use tsetse/fly precautions and confirm before any treatment.",
        ),
    )

    fun forGroup(group: WildlifeGroup): List<WildlifeDisease> =
        diseases.filter { group in it.groups }
}
