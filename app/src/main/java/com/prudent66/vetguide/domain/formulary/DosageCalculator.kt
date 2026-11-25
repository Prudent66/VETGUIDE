package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup

/** Route of administration offered by the offline formularies. */
enum class DoseRoute(val label: String) {
    IM("IM"),
    SC("SC"),
    IV("IV"),
    PO("PO"),
    WATER("Water"),
    DART("Dart"),
    ORAL("Oral"),
    BATH("Bath"),
    TOPICAL("Topical"),
}

/** Standard per-kilogram dose range; the recommended value is its midpoint. */
data class DoseRange(
    val minMgPerKg: Double,
    val maxMgPerKg: Double,
) {
    fun recommended(): Double = (minMgPerKg + maxMgPerKg) / 2.0
}

/**
 * One offline formulary entry. Concentrations are expressed in mg/mL so a
 * bottle strength (including percentages) can be converted directly into a
 * syringe volume. Dose ranges and withdrawal periods are decision-support
 * references and must always be verified by the attending professional.
 */
data class FormularyDrug(
    val id: String,
    val name: String,
    val doseBySpecies: Map<Species, DoseRange>,
    val concentrationsMgPerMl: List<Double>,
    val route: DoseRoute,
    val frequency: String,
    val meatWithdrawalDays: Int,
    val milkWithdrawalDays: Int,
    val contraindications: Map<Species, String> = emptyMap(),
    /** True for an analyzed drug added at runtime with conservative fallback dosing. */
    val isAnalyzedFallback: Boolean = false,
) {
    val defaultConcentrationMgPerMl: Double get() = concentrationsMgPerMl.first()
}

/** Fully calculated dose, syringe draw volume and safety/withdrawal summary. */
data class DosageResult(
    val drugName: String,
    val species: Species,
    val weightKg: Double,
    val doseRange: DoseRange,
    val selectedMgPerKg: Double,
    val doseMg: Double,
    val concentrationMgPerMl: Double,
    val syringeMl: Double,
    val route: DoseRoute,
    val frequency: String,
    val meatWithdrawalDays: Int,
    val milkWithdrawalDays: Int,
    val safetyAlerts: List<String>,
)

private fun dose(min: Double, max: Double): DoseRange = DoseRange(min, max)

private val cattleSheepGoatPig = listOf(Species.CATTLE, Species.GOAT, Species.SHEEP, Species.PIG)
private val companion = listOf(Species.DOG, Species.CAT)
private val globalFarmSpecies = listOf(Species.BUFFALO, Species.CAMEL, Species.RABBIT)
private val terrestrialDomestic =
    cattleSheepGoatPig + companion + globalFarmSpecies + Species.EQUINE

/**
 * Offline veterinary formulary covering common livestock, poultry and
 * companion-animal drugs. No network access and no patient data leave the app.
 */
val formulary: List<FormularyDrug> = listOf(
    FormularyDrug(
        id = "oxytetracycline-5",
        name = "Oxytetracycline 5%",
        doseBySpecies = mapOf(
            Species.CATTLE to dose(10.0, 20.0),
            Species.GOAT to dose(10.0, 20.0),
            Species.SHEEP to dose(10.0, 20.0),
            Species.PIG to dose(10.0, 20.0),
            Species.POULTRY to dose(15.0, 25.0),
            Species.DOG to dose(10.0, 20.0),
            Species.CAT to dose(10.0, 20.0),
            Species.EQUINE to dose(5.0, 10.0),
            Species.BUFFALO to dose(10.0, 20.0),
            Species.CAMEL to dose(10.0, 20.0),
            Species.RABBIT to dose(10.0, 20.0),
        ),
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.IM,
        frequency = "Once daily",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 3,
    ),
    FormularyDrug(
        id = "oxytetracycline-20",
        name = "Oxytetracycline 20%",
        doseBySpecies = mapOf(
            Species.CATTLE to dose(10.0, 20.0),
            Species.GOAT to dose(10.0, 20.0),
            Species.SHEEP to dose(10.0, 20.0),
            Species.PIG to dose(10.0, 20.0),
            Species.POULTRY to dose(15.0, 25.0),
            Species.BUFFALO to dose(10.0, 20.0),
            Species.CAMEL to dose(10.0, 20.0),
        ),
        concentrationsMgPerMl = listOf(200.0),
        route = DoseRoute.IM,
        frequency = "Once daily (long acting)",
        meatWithdrawalDays = 14,
        milkWithdrawalDays = 4,
    ),
    FormularyDrug(
        id = "penicillin-streptomycin",
        name = "Penicillin-Streptomycin",
        doseBySpecies = (cattleSheepGoatPig + companion + globalFarmSpecies + Species.EQUINE)
            .associateWith { dose(10.0, 15.0) },
        concentrationsMgPerMl = listOf(150.0),
        route = DoseRoute.IM,
        frequency = "Every 12 hours",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 2,
    ),
    FormularyDrug(
        id = "ivermectin-1",
        name = "Ivermectin 1%",
        doseBySpecies = (cattleSheepGoatPig + companion + globalFarmSpecies + Species.EQUINE)
            .associateWith { dose(0.2, 0.3) },
        concentrationsMgPerMl = listOf(10.0),
        route = DoseRoute.SC,
        frequency = "Single dose; repeat as directed",
        meatWithdrawalDays = 8,
        milkWithdrawalDays = 4,
        contraindications = mapOf(
            Species.DOG to "Confirm heartworm status before treatment in dogs.",
        ),
    ),
    FormularyDrug(
        id = "albendazole",
        name = "Albendazole",
        doseBySpecies = mapOf(
            Species.CATTLE to dose(5.0, 10.0),
            Species.GOAT to dose(5.0, 10.0),
            Species.SHEEP to dose(5.0, 10.0),
            Species.PIG to dose(5.0, 10.0),
            Species.POULTRY to dose(5.0, 10.0),
            Species.DOG to dose(5.0, 10.0),
            Species.BUFFALO to dose(5.0, 10.0),
            Species.CAMEL to dose(5.0, 10.0),
            Species.RABBIT to dose(5.0, 10.0),
        ),
        concentrationsMgPerMl = listOf(50.0, 100.0),
        route = DoseRoute.PO,
        frequency = "Single dose; repeat in 14 days if needed",
        meatWithdrawalDays = 14,
        milkWithdrawalDays = 5,
    ),
    FormularyDrug(
        id = "amprolium",
        name = "Amprolium",
        doseBySpecies = mapOf(
            Species.POULTRY to dose(20.0, 40.0),
            Species.GOAT to dose(20.0, 40.0),
            Species.SHEEP to dose(20.0, 40.0),
        ),
        concentrationsMgPerMl = listOf(200.0),
        route = DoseRoute.WATER,
        frequency = "Daily for 5–7 days",
        meatWithdrawalDays = 5,
        milkWithdrawalDays = 0,
        contraindications = mapOf(
            Species.CATTLE to "Not for cattle — risk of thiamine deficiency.",
        ),
    ),
    FormularyDrug(
        id = "tylosin",
        name = "Tylosin",
        doseBySpecies = mapOf(
            Species.POULTRY to dose(5.0, 10.0),
            Species.PIG to dose(5.0, 10.0),
            Species.CATTLE to dose(5.0, 10.0),
            Species.GOAT to dose(5.0, 10.0),
            Species.SHEEP to dose(5.0, 10.0),
            Species.DOG to dose(5.0, 10.0),
            Species.CAT to dose(5.0, 10.0),
        ),
        concentrationsMgPerMl = listOf(50.0, 200.0),
        route = DoseRoute.IM,
        frequency = "Every 12 hours",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 3,
        contraindications = mapOf(
            Species.EQUINE to "Tylosin is not recommended in horses.",
        ),
    ),
    FormularyDrug(
        id = "meloxicam",
        name = "Meloxicam",
        doseBySpecies = (companion + cattleSheepGoatPig + globalFarmSpecies + Species.EQUINE)
            .associateWith { dose(0.2, 0.4) },
        concentrationsMgPerMl = listOf(5.0, 20.0),
        route = DoseRoute.SC,
        frequency = "Once daily",
        meatWithdrawalDays = 5,
        milkWithdrawalDays = 2,
        contraindications = mapOf(
            Species.CAT to "Use with caution in cats with reduced kidney function.",
        ),
    ),
    FormularyDrug(
        id = "enrofloxacin",
        name = "Enrofloxacin",
        doseBySpecies = mapOf(
            Species.CATTLE to dose(2.5, 5.0),
            Species.POULTRY to dose(2.5, 5.0),
            Species.PIG to dose(2.5, 5.0),
            Species.GOAT to dose(2.5, 5.0),
            Species.SHEEP to dose(2.5, 5.0),
            Species.DOG to dose(2.5, 5.0),
            Species.CAT to dose(2.5, 5.0),
            Species.RABBIT to dose(2.5, 5.0),
        ),
        concentrationsMgPerMl = listOf(50.0, 100.0),
        route = DoseRoute.IM,
        frequency = "Once daily",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 2,
        contraindications = mapOf(
            Species.DOG to "Avoid in growing dogs — cartilage risk.",
        ),
    ),
    FormularyDrug(
        id = "diminazene-aceturate",
        name = "Diminazene Aceturate",
        doseBySpecies = mapOf(
            Species.CATTLE to dose(3.5, 5.0),
            Species.GOAT to dose(3.5, 5.0),
            Species.SHEEP to dose(3.5, 5.0),
            Species.BUFFALO to dose(3.5, 5.0),
            Species.CAMEL to dose(3.5, 5.0),
        ),
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.IM,
        frequency = "Single dose; repeat after 14 days if needed",
        meatWithdrawalDays = 14,
        milkWithdrawalDays = 7,
        contraindications = mapOf(
            Species.EQUINE to "Not recommended in horses.",
        ),
    ),
    FormularyDrug(
        id = "oxytetracycline-aquatic",
        name = "Oxytetracycline (Aquatic)",
        doseBySpecies = mapOf(Species.FISH to dose(15.0, 25.0)),
        concentrationsMgPerMl = listOf(200.0),
        route = DoseRoute.BATH,
        frequency = "Bath or water medication — verify species and water volume",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 0,
        contraindications = mapOf(
            Species.FISH to "Confirm species, water chemistry and local regulations before medicating.",
        ),
    ),
    FormularyDrug(
        id = "florfenicol-aquatic",
        name = "Florfenicol (Aquatic)",
        doseBySpecies = mapOf(Species.FISH to dose(10.0, 15.0)),
        concentrationsMgPerMl = listOf(200.0),
        route = DoseRoute.WATER,
        frequency = "Medicated water/feed — verify local aquaculture rules",
        meatWithdrawalDays = 12,
        milkWithdrawalDays = 0,
    ),
    FormularyDrug(
        id = "amoxicillin-la",
        name = "Amoxicillin LA",
        doseBySpecies = terrestrialDomestic.associateWith { dose(5.0, 10.0) },
        concentrationsMgPerMl = listOf(150.0),
        route = DoseRoute.IM,
        frequency = "Every 48 hours (long acting)",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 3,
    ),
    FormularyDrug(
        id = "amoxicillin",
        name = "Amoxicillin",
        doseBySpecies = terrestrialDomestic.associateWith { dose(5.0, 10.0) },
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.IM,
        frequency = "Every 12 hours",
        meatWithdrawalDays = 5,
        milkWithdrawalDays = 2,
    ),
    FormularyDrug(
        id = "doxycycline",
        name = "Doxycycline",
        doseBySpecies = terrestrialDomestic.associateWith { dose(3.0, 5.0) },
        concentrationsMgPerMl = listOf(50.0, 100.0),
        route = DoseRoute.PO,
        frequency = "Once daily",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 3,
    ),
    FormularyDrug(
        id = "gentamicin",
        name = "Gentamicin",
        doseBySpecies = terrestrialDomestic.associateWith { dose(2.0, 4.0) },
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.IM,
        frequency = "Every 12 hours; short course",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 3,
        contraindications = mapOf(
            Species.CAT to "Use only with renal monitoring — narrow safety margin in cats.",
        ),
    ),
    FormularyDrug(
        id = "ceftiofur",
        name = "Ceftiofur",
        doseBySpecies = (cattleSheepGoatPig + globalFarmSpecies + Species.EQUINE)
            .associateWith { dose(1.0, 2.0) },
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.SC,
        frequency = "Once daily",
        meatWithdrawalDays = 4,
        milkWithdrawalDays = 0,
    ),
    FormularyDrug(
        id = "flunixin-meglumine",
        name = "Flunixin Meglumine",
        doseBySpecies = (cattleSheepGoatPig + globalFarmSpecies + Species.EQUINE)
            .associateWith { dose(1.0, 2.0) },
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.IV,
        frequency = "Once daily; short course only",
        meatWithdrawalDays = 4,
        milkWithdrawalDays = 1,
        contraindications = mapOf(
            Species.EQUINE to "Do not exceed the labelled course in horses.",
        ),
    ),
    FormularyDrug(
        id = "calcium-borogluconate",
        name = "Calcium Borogluconate",
        doseBySpecies = (listOf(Species.CATTLE, Species.GOAT, Species.SHEEP) + globalFarmSpecies)
            .associateWith { dose(20.0, 40.0) },
        concentrationsMgPerMl = listOf(250.0),
        route = DoseRoute.IV,
        frequency = "Slow IV under direct veterinary supervision",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
        contraindications = mapOf(
            Species.CATTLE to "Administer slowly and monitor cardiac response.",
        ),
    ),
    FormularyDrug(
        id = "vitamin-b-complex",
        name = "Vitamin B-Complex",
        doseBySpecies = terrestrialDomestic.associateWith { dose(1.0, 2.0) },
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.IM,
        frequency = "Once daily or as directed",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
    ),
    FormularyDrug(
        id = "topical-antibiotic-ointment",
        name = "Topical Antibiotic Ointment",
        doseBySpecies = terrestrialDomestic.associateWith { dose(1.0, 2.0) },
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.TOPICAL,
        frequency = "Apply to a cleaned lesion once or twice daily",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
    ),
    FormularyDrug(
        id = "wound-spray-antiseptic-oil",
        name = "Wound Spray / Antiseptic Oil",
        doseBySpecies = terrestrialDomestic.associateWith { dose(1.0, 2.0) },
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.TOPICAL,
        frequency = "Apply to a cleaned wound as directed",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
    ),
)

/** Domestic drugs applicable to a species — keeps wild patients out of this table. */
fun formularyForSpecies(species: Species): List<FormularyDrug> =
    formulary.filter { species in it.doseBySpecies }

fun findDrug(id: String): FormularyDrug? = formulary.firstOrNull { it.id == id }

fun findDrugByName(name: String): FormularyDrug? =
    formulary.firstOrNull { it.name.equals(name, ignoreCase = true) }

/**
 * Calculates the recommended dose, syringe draw volume, route/schedule,
 * withdrawal periods and species safety alerts. Returns null when the species
 * is unsupported or the weight/concentration is not a positive number.
 */
fun calculateDosage(
    drug: FormularyDrug,
    species: Species,
    weightKg: Double,
    concentrationMgPerMl: Double = drug.defaultConcentrationMgPerMl,
): DosageResult? {
    val range = drug.doseBySpecies[species]
    if (range == null || weightKg <= 0.0 || concentrationMgPerMl <= 0.0) return null
    val perKg = range.recommended()
    return DosageResult(
        drugName = drug.name,
        species = species,
        weightKg = weightKg,
        doseRange = range,
        selectedMgPerKg = perKg,
        doseMg = perKg * weightKg,
        concentrationMgPerMl = concentrationMgPerMl,
        syringeMl = (perKg * weightKg) / concentrationMgPerMl,
        route = drug.route,
        frequency = drug.frequency,
        meatWithdrawalDays = drug.meatWithdrawalDays,
        milkWithdrawalDays = drug.milkWithdrawalDays,
        safetyAlerts = listOfNotNull(drug.contraindications[species]),
    )
}

/** Generic drug keywords mapped to formulary product ids. */
private val drugKeywords = mapOf(
    "ivermectin" to "ivermectin-1",
    "penicillin" to "penicillin-streptomycin",
    "streptomycin" to "penicillin-streptomycin",
    "albendazole" to "albendazole",
    "amprolium" to "amprolium",
    "florfenicol" to "florfenicol-aquatic",
    "tylosin" to "tylosin",
    "meloxicam" to "meloxicam",
    "enrofloxacin" to "enrofloxacin",
    "diminazene" to "diminazene-aceturate",
    "amoxicillin" to "amoxicillin",
    "doxycycline" to "doxycycline",
    "gentamicin" to "gentamicin",
    "ceftiofur" to "ceftiofur",
    "flunixin" to "flunixin-meglumine",
    "calcium borogluconate" to "calcium-borogluconate",
    "vitamin b-complex" to "vitamin-b-complex",
    "antibiotic ointment" to "topical-antibiotic-ointment",
    "antiseptic oil" to "wound-spray-antiseptic-oil",
    "wound spray" to "wound-spray-antiseptic-oil",
    "neosporin" to "topical-antibiotic-ointment",
    "bacitracin" to "topical-antibiotic-ointment",
    "polysporin" to "topical-antibiotic-ointment",
    "triple antibiotic" to "topical-antibiotic-ointment",
    "mupirocin" to "topical-antibiotic-ointment",
)

/**
 * Matches prescription text case-insensitively. Generic names such as
 * "ivermectin" select the matching product; the 20% oxytetracycline product
 * is preferred when the text mentions 20 or 200 mg/mL.
 */
fun matchDrugFromText(text: String): FormularyDrug? {
    val haystack = text.lowercase()
    val id = if ("oxytetracycline" in haystack) {
        if ("fish" in haystack || "aquatic" in haystack || "bath" in haystack) {
            "oxytetracycline-aquatic"
        } else if ("20" in haystack || "200" in haystack) {
            "oxytetracycline-20"
        } else {
            "oxytetracycline-5"
        }
    } else {
        drugKeywords.entries.firstOrNull { it.key in haystack }?.value
    }
    return id?.let(::findDrug)
}

/*
 * Wildlife formulary — deliberately independent of the domestic `formulary`
 * above. Entries are keyed by WildlifeGroup (or a concrete wild subtype); no
 * domestic Species key, dose, warning or fallback is shared in either
 * direction. MG/KG ranges are decision-support references for wildlife
 * professionals and must be verified against current literature before use.
 */

/** One wildlife entry, keyed strictly to wildlife groups and subtypes. */
data class WildlifeFormularyEntry(
    val id: String,
    val name: String,
    val doseByGroup: Map<WildlifeGroup, DoseRange>,
    val concentrationsMgPerMl: List<Double>,
    val route: DoseRoute,
    val frequency: String,
    val meatWithdrawalDays: Int,
    val milkWithdrawalDays: Int,
    val contraindications: Map<WildlifeGroup, String> = emptyMap(),
    /** Overrides [doseByGroup] for named wild animals (e.g. African Wild Dog). */
    val doseByAnimal: Map<String, DoseRange> = emptyMap(),
    /** True for an analyzed wild drug added at runtime with fallback dosing. */
    val isAnalyzedFallback: Boolean = false,
) {
    val defaultConcentrationMgPerMl: Double get() = concentrationsMgPerMl.first()
}

/** Fully calculated wildlife dose and syringe draw volume. */
data class WildlifeDosageResult(
    val drugName: String,
    val animalLabel: String,
    val group: WildlifeGroup,
    val weightKg: Double,
    val doseRange: DoseRange,
    val selectedMgPerKg: Double,
    val doseMg: Double,
    val concentrationMgPerMl: Double,
    val syringeMl: Double,
    val route: DoseRoute,
    val frequency: String,
    val meatWithdrawalDays: Int,
    val milkWithdrawalDays: Int,
    val safetyAlerts: List<String>,
)

private val allWildGroups = WildlifeGroup.entries.toSet()

/** Dedicated wild-animal formulary; never consulted by [calculateDosage]. */
val wildlifeFormulary: List<WildlifeFormularyEntry> = listOf(
    WildlifeFormularyEntry(
        id = "wild-oxytetracycline-20",
        name = "Oxytetracycline 20% (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(10.0, 20.0),
            WildlifeGroup.WILD_CANIDS to dose(10.0, 20.0),
            WildlifeGroup.WILD_FELIDS to dose(10.0, 20.0),
            WildlifeGroup.PRIMATES to dose(10.0, 20.0),
            WildlifeGroup.WILD_BIRDS to dose(15.0, 25.0),
            WildlifeGroup.REPTILES to dose(10.0, 20.0),
            WildlifeGroup.SMALL_WILD_MAMMALS to dose(10.0, 20.0),
        ),
        concentrationsMgPerMl = listOf(200.0),
        route = DoseRoute.IM,
        frequency = "Once daily (long acting); verify with wildlife protocol",
        meatWithdrawalDays = 14,
        milkWithdrawalDays = 4,
        contraindications = mapOf(
            WildlifeGroup.WILD_BIRDS to "Confirm species-specific safety before injectable tetracyclines in birds.",
        ),
        doseByAnimal = mapOf("African Wild Dog" to dose(14.0, 18.0)),
    ),
    WildlifeFormularyEntry(
        id = "wild-ivermectin",
        name = "Ivermectin 1% (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(0.2, 0.3),
            WildlifeGroup.WILD_CANIDS to dose(0.2, 0.3),
            WildlifeGroup.PRIMATES to dose(0.2, 0.3),
            WildlifeGroup.SMALL_WILD_MAMMALS to dose(0.2, 0.3),
        ),
        concentrationsMgPerMl = listOf(10.0),
        route = DoseRoute.SC,
        frequency = "Single dose; repeat only under wildlife authority direction",
        meatWithdrawalDays = 8,
        milkWithdrawalDays = 4,
        contraindications = mapOf(
            WildlifeGroup.SMALL_WILD_MAMMALS to "Use with extreme caution in bats and pangolins — species sensitivity reported.",
        ),
    ),
    WildlifeFormularyEntry(
        id = "wild-ketamine-xylazine",
        name = "Ketamine / Xylazine (wild dart)",
        doseByGroup = allWildGroups.associateWith { dose(2.0, 5.0) },
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.DART,
        frequency = "Single restraint dose — qualified wildlife personnel only",
        meatWithdrawalDays = 14,
        milkWithdrawalDays = 7,
        contraindications = mapOf(
            WildlifeGroup.WILD_RUMINANTS to "Capture myopathy risk — minimise chase time and monitor recovery.",
        ),
    ),
    WildlifeFormularyEntry(
        id = "wild-enrofloxacin",
        name = "Enrofloxacin (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_CANIDS to dose(5.0, 10.0),
            WildlifeGroup.WILD_FELIDS to dose(5.0, 10.0),
            WildlifeGroup.WILD_BIRDS to dose(5.0, 10.0),
            WildlifeGroup.SMALL_WILD_MAMMALS to dose(5.0, 10.0),
        ),
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.ORAL,
        frequency = "Once daily; verify species-specific safety",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 2,
        contraindications = mapOf(
            WildlifeGroup.WILD_BIRDS to "Avoid indiscriminate fluoroquinolone use in migratory birds.",
        ),
    ),
    WildlifeFormularyEntry(
        id = "wild-albendazole",
        name = "Albendazole (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(5.0, 10.0),
            WildlifeGroup.PRIMATES to dose(5.0, 10.0),
            WildlifeGroup.SMALL_WILD_MAMMALS to dose(5.0, 10.0),
        ),
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.ORAL,
        frequency = "Single dose; repeat only under wildlife authority direction",
        meatWithdrawalDays = 14,
        milkWithdrawalDays = 5,
    ),
    WildlifeFormularyEntry(
        id = "wild-meloxicam",
        name = "Meloxicam (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(0.2, 0.4),
            WildlifeGroup.WILD_CANIDS to dose(0.2, 0.4),
            WildlifeGroup.WILD_FELIDS to dose(0.2, 0.4),
            WildlifeGroup.PRIMATES to dose(0.2, 0.4),
        ),
        concentrationsMgPerMl = listOf(20.0),
        route = DoseRoute.IM,
        frequency = "Once daily; ensure hydration and renal monitoring",
        meatWithdrawalDays = 5,
        milkWithdrawalDays = 2,
        contraindications = mapOf(
            WildlifeGroup.WILD_RUMINANTS to "Use cautiously in dehydrated or debilitated animals.",
        ),
    ),
    WildlifeFormularyEntry(
        id = "wild-diminazene",
        name = "Diminazene Aceturate (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(3.5, 5.0),
            WildlifeGroup.WILD_CANIDS to dose(3.5, 5.0),
            WildlifeGroup.PRIMATES to dose(3.5, 5.0),
        ),
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.IM,
        frequency = "Single dose; repeat after 14 days if directed",
        meatWithdrawalDays = 14,
        milkWithdrawalDays = 7,
        contraindications = mapOf(
            WildlifeGroup.PRIMATES to "Confirm trypanosomiasis before treatment in primates.",
        ),
    ),
    WildlifeFormularyEntry(
        id = "wild-amoxicillin",
        name = "Amoxicillin (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(5.0, 10.0),
            WildlifeGroup.WILD_CANIDS to dose(5.0, 10.0),
            WildlifeGroup.WILD_FELIDS to dose(5.0, 10.0),
            WildlifeGroup.PRIMATES to dose(5.0, 10.0),
            WildlifeGroup.SMALL_WILD_MAMMALS to dose(5.0, 10.0),
        ),
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.IM,
        frequency = "Every 12 hours; verify with wildlife protocol",
        meatWithdrawalDays = 5,
        milkWithdrawalDays = 2,
    ),
    WildlifeFormularyEntry(
        id = "wild-doxycycline",
        name = "Doxycycline (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(3.0, 5.0),
            WildlifeGroup.WILD_CANIDS to dose(3.0, 5.0),
            WildlifeGroup.WILD_FELIDS to dose(3.0, 5.0),
            WildlifeGroup.PRIMATES to dose(3.0, 5.0),
            WildlifeGroup.WILD_BIRDS to dose(3.0, 5.0),
            WildlifeGroup.SMALL_WILD_MAMMALS to dose(3.0, 5.0),
        ),
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.ORAL,
        frequency = "Once daily; verify species-specific safety",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 3,
    ),
    WildlifeFormularyEntry(
        id = "wild-ceftiofur",
        name = "Ceftiofur (wild)",
        doseByGroup = mapOf(
            WildlifeGroup.WILD_RUMINANTS to dose(1.0, 2.0),
            WildlifeGroup.WILD_CANIDS to dose(1.0, 2.0),
            WildlifeGroup.PRIMATES to dose(1.0, 2.0),
            WildlifeGroup.WILD_BIRDS to dose(1.0, 2.0),
        ),
        concentrationsMgPerMl = listOf(50.0),
        route = DoseRoute.SC,
        frequency = "Once daily under wildlife authority direction",
        meatWithdrawalDays = 4,
        milkWithdrawalDays = 0,
    ),
    WildlifeFormularyEntry(
        id = "wild-atropine",
        name = "Atropine / Supportive (wild)",
        doseByGroup = allWildGroups.associateWith { dose(0.02, 0.05) },
        concentrationsMgPerMl = listOf(1.0),
        route = DoseRoute.IM,
        frequency = "Emergency use only — veterinary direction required",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
        contraindications = mapOf(
            WildlifeGroup.WILD_RUMINANTS to "Species sensitivity varies — do not use without veterinary direction.",
        ),
    ),
    WildlifeFormularyEntry(
        id = "wild-topical-wound",
        name = "Topical Wound / Antiseptic Ointment (wild)",
        doseByGroup = allWildGroups.associateWith { dose(1.0, 2.0) },
        concentrationsMgPerMl = listOf(100.0),
        route = DoseRoute.TOPICAL,
        frequency = "Apply to a cleaned wound under restraint protocol",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
    ),
)

fun findWildlifeDrug(id: String): WildlifeFormularyEntry? =
    wildlifeFormulary.firstOrNull { it.id == id }

fun wildlifeDrugsFor(group: WildlifeGroup): List<WildlifeFormularyEntry> =
    wildlifeFormulary.filter { group in it.doseByGroup }

private val wildlifeDrugKeywords = listOf(
    "oxytetracycline" to "wild-oxytetracycline-20",
    "ivermectin" to "wild-ivermectin",
    "enrofloxacin" to "wild-enrofloxacin",
    "albendazole" to "wild-albendazole",
    "meloxicam" to "wild-meloxicam",
    "diminazene" to "wild-diminazene",
    "amoxicillin" to "wild-amoxicillin",
    "doxycycline" to "wild-doxycycline",
    "ceftiofur" to "wild-ceftiofur",
    "atropine" to "wild-atropine",
)

/** Auto-selects a wildlife product from prescription text for a wild patient. */
fun matchWildlifeDrugFromText(text: String, group: WildlifeGroup): WildlifeFormularyEntry? {
    val haystack = text.lowercase()
    val id = wildlifeDrugKeywords.firstOrNull { it.first in haystack }?.second
        ?: when {
            "ointment" in haystack || "antiseptic" in haystack -> "wild-topical-wound"
            "ketamine" in haystack || "xylazine" in haystack -> "wild-ketamine-xylazine"
            else -> null
        }
    return id?.let(::findWildlifeDrug)?.takeIf { group in it.doseByGroup }
}

/**
 * Wildlife dosing: resolves a subtype-specific range first, then the group
 * range. Only [WildlifeGroup]/wild subtype keys are consulted — no domestic
 * species, [Species.OTHER] lookup or shared formulary fallback is possible.
 */
fun calculateWildlifeDosage(
    drug: WildlifeFormularyEntry,
    group: WildlifeGroup,
    weightKg: Double,
    concentrationMgPerMl: Double = drug.defaultConcentrationMgPerMl,
    wildAnimal: String? = null,
): WildlifeDosageResult? {
    val range = drug.doseByAnimal[wildAnimal] ?: drug.doseByGroup[group]
    return if (range == null || weightKg <= 0.0 || concentrationMgPerMl <= 0.0) {
        null
    } else {
        val perKg = range.recommended()
        WildlifeDosageResult(
            drugName = drug.name,
            animalLabel = listOfNotNull(wildAnimal, group.label).joinToString(" · "),
            group = group,
            weightKg = weightKg,
            doseRange = range,
            selectedMgPerKg = perKg,
            doseMg = perKg * weightKg,
            concentrationMgPerMl = concentrationMgPerMl,
            syringeMl = (perKg * weightKg) / concentrationMgPerMl,
            route = drug.route,
            frequency = drug.frequency,
            meatWithdrawalDays = drug.meatWithdrawalDays,
            milkWithdrawalDays = drug.milkWithdrawalDays,
            safetyAlerts = listOfNotNull(drug.contraindications[group]),
        )
    }
}
