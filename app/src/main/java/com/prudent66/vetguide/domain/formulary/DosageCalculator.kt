package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species

/** Route of administration offered by the offline formulary. */
enum class DoseRoute(val label: String) {
    IM("IM"),
    SC("SC"),
    IV("IV"),
    PO("PO"),
    WATER("Water"),
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
        doseBySpecies = (cattleSheepGoatPig + companion + Species.EQUINE).associateWith {
            dose(10.0, 15.0)
        },
        concentrationsMgPerMl = listOf(150.0),
        route = DoseRoute.IM,
        frequency = "Every 12 hours",
        meatWithdrawalDays = 7,
        milkWithdrawalDays = 2,
    ),
    FormularyDrug(
        id = "ivermectin-1",
        name = "Ivermectin 1%",
        doseBySpecies = (cattleSheepGoatPig + companion + Species.EQUINE).associateWith {
            dose(0.2, 0.3)
        },
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
        doseBySpecies = (companion + cattleSheepGoatPig + Species.EQUINE).associateWith {
            dose(0.2, 0.4)
        },
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
)

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

/**
 * Picks the formulary drug mentioned in a prescription line, if any. Matching
 * is case-insensitive and uses both drug names and stable ids, so an analysis
 * line such as "Tylosin 50 mg/kg PO" selects the right entry immediately.
 */
fun matchDrugFromText(text: String): FormularyDrug? {
    val haystack = text.lowercase()
    return formulary.firstOrNull { drug ->
        haystack.contains(drug.name.lowercase()) ||
            haystack.contains(drug.id.replace('-', ' '))
    }
}
