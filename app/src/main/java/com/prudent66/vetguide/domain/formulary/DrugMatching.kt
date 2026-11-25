package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup

/**
 * Condition-aware drug selection and analyzed-drug discovery. Domestic keys,
 * products and fallbacks are deliberately separate from wildlife ones: no id,
 * DoseRange, warning or lookup crosses the two pathways.
 */

private val domesticConditionDrugs = mapOf(
    "abscess" to "topical-antibiotic-ointment",
    "wound sepsis" to "wound-spray-antiseptic-oil",
    "mastitis" to "penicillin-streptomycin",
    "pneumonia" to "oxytetracycline-20",
    "pleuropneumonia" to "ceftiofur",
    "helminthosis" to "albendazole",
    "coccidiosis" to "amprolium",
    "parvovirus" to "enrofloxacin",
    "newcastle" to "oxytetracycline-5",
    "foot-and-mouth" to "oxytetracycline-5",
    "peste" to "oxytetracycline-20",
    "swine fever" to "vitamin-b-complex",
    "colic" to "flunixin-meglumine",
    "pyrexia" to "meloxicam",
    "trypanosomiasis" to "diminazene-aceturate",
    "bacterial infection" to "amoxicillin",
    "calcium" to "calcium-borogluconate",
)

private val wildConditionDrugs = mapOf(
    "skin lesion" to "wild-topical-wound",
    "bite injury" to "wild-topical-wound",
    "respiratory signs" to "wild-oxytetracycline-20",
    "parasite burden" to "wild-albendazole",
    "restraint" to "wild-ketamine-xylazine",
    "collapse" to "wild-atropine",
    "trypanosome" to "wild-diminazene",
    "septicaemia" to "wild-amoxicillin",
)

private val domesticAnalyzedNames = listOf(
    "Amoxicillin LA", "Amoxicillin", "Doxycycline", "Gentamicin", "Ceftiofur",
    "Flunixin Meglumine", "Calcium Borogluconate", "Vitamin B-Complex",
    "Topical Antibiotic Ointment", "Wound Spray / Antiseptic Oil",
    "Metronidazole", "Praziquantel", "Fenbendazole", "Dexamethasone",
    "Sulfadimidine", "Trimethoprim", "Levamisole", "Closantel",
    "Doramectin", "Furosemide", "Prednisolone",
)

private val wildAnalyzedNames = listOf(
    "Amoxicillin (wild)", "Doxycycline (wild)", "Ceftiofur (wild)",
    "Atropine / Supportive (wild)", "Topical Wound / Antiseptic Ointment (wild)",
    "Metronidazole (wild)", "Ketamine (wild)", "Xylazine (wild)",
    "Praziquantel (wild)", "Furosemide (wild)",
)

private val genericTopicalRegex = Regex(
    """\b[A-Z][A-Za-z-]*(?:\s+[A-Za-z-]+){0,2}\s+(?:ointment|oil|spray|injection|drench)\b""",
)

/** Medication names found in findings/recommendations, known ones first. */
internal fun extractedDrugNames(text: String, dictionary: List<String>): List<String> {
    val lower = text.lowercase()
    val known = dictionary.filter { it.lowercase() in lower }
    val generic = genericTopicalRegex.findAll(text)
        .map { it.value.trim() }
        .filter { phrase -> dictionary.none { it.lowercase() in phrase.lowercase() } }
        .filter { it.length <= 60 }
    return (known + generic).distinctBy { it.lowercase() }
}

/** Preferred domestic product for a suspected condition, if one is mapped. */
fun domesticConditionDrug(condition: String?): FormularyDrug? {
    val key = condition?.lowercase() ?: return null
    return domesticConditionDrugs.entries.firstOrNull { it.key in key }
        ?.value?.let(::findDrug)
}

/** Preferred wildlife product for a condition, restricted to the selected group. */
fun wildConditionDrug(condition: String?, group: WildlifeGroup): WildlifeFormularyEntry? {
    val key = condition?.lowercase() ?: return null
    return wildConditionDrugs.entries.firstOrNull { it.key in key }
        ?.value?.let(::findWildlifeDrug)
        ?.takeIf { group in it.doseByGroup }
}

/** Best domestic default: explicit text, then condition, then analyzed drug. */
fun bestDomesticDrug(
    text: String,
    condition: String?,
    species: Species,
): FormularyDrug? {
    val drugs = domesticDrugsFor(text)
    return matchDrugFromText(text)
        ?: domesticConditionDrug(condition)
        ?: drugs.firstOrNull { it.isAnalyzedFallback && species in it.doseBySpecies }
        ?: drugs.firstOrNull { species in it.doseBySpecies }
}

/** Best wildlife default, using only the independent wildlife formulary. */
fun bestWildDrug(
    text: String,
    condition: String?,
    group: WildlifeGroup,
): WildlifeFormularyEntry? {
    val drugs = wildDrugsFor(text, group)
    return matchWildlifeDrugFromText(text, group)
        ?: wildConditionDrug(condition, group)
        ?: drugs.firstOrNull { it.isAnalyzedFallback && group in it.doseByGroup }
        ?: drugs.firstOrNull { group in it.doseByGroup }
}

/** Full domestic picker: static products plus any analyzed drug not yet listed. */
fun domesticDrugsFor(text: String): List<FormularyDrug> {
    val analyzed = extractedDrugNames(text, domesticAnalyzedNames)
        .filter { name -> formulary.none { it.name.equals(name, ignoreCase = true) } }
        .map(::syntheticDomesticDrug)
    return formulary + analyzed
}

/** Wildlife picker for one group: static group products plus analyzed additions. */
fun wildDrugsFor(text: String, group: WildlifeGroup): List<WildlifeFormularyEntry> {
    val static = wildlifeDrugsFor(group)
    val analyzed = extractedDrugNames(text, wildAnalyzedNames)
        .filter { name -> static.none { it.name.equals(name, ignoreCase = true) } }
        .map { syntheticWildDrug(it, group) }
    return static + analyzed
}

private fun syntheticDomesticDrug(name: String): FormularyDrug {
    val lower = name.lowercase()
    val topical = listOf("ointment", "oil", "spray", "drench").any { it in lower }
    return FormularyDrug(
        id = "analyzed-domestic-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}",
        name = "$name (analyzed — verify dose)",
        doseBySpecies = Species.entries.filter { it != Species.OTHER }
            .associateWith { DoseRange(5.0, 10.0) },
        concentrationsMgPerMl = listOf(50.0),
        route = if (topical) DoseRoute.TOPICAL else DoseRoute.IM,
        frequency = "Verify frequency against a current formulary",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
        isAnalyzedFallback = true,
    )
}

private fun syntheticWildDrug(name: String, group: WildlifeGroup): WildlifeFormularyEntry {
    val lower = name.lowercase()
    val topical = listOf("ointment", "oil", "spray", "drench").any { it in lower }
    return WildlifeFormularyEntry(
        id = "analyzed-wild-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}",
        name = "$name (wild; analyzed — verify dose)",
        doseByGroup = WildlifeGroup.entries.associateWith { DoseRange(5.0, 10.0) },
        concentrationsMgPerMl = listOf(50.0),
        route = if (topical) DoseRoute.TOPICAL else DoseRoute.IM,
        frequency = "Verify frequency against a current wildlife protocol",
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
        isAnalyzedFallback = true,
        // Keeps the synthetic entry bound to the group that requested it.
        contraindications = if (group == WildlifeGroup.SMALL_WILD_MAMMALS) {
            mapOf(group to "Confirm species sensitivity before use.")
        } else {
            emptyMap()
        },
    )
}
