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
    "gut worm" to "albendazole",
    "internal parasite" to "albendazole",
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
    "gut worm" to "wild-albendazole",
    "internal parasite" to "wild-albendazole",
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
    "Doramectin", "Furosemide", "Prednisolone", "Pyrantel Pamoate",
    "Ivermectin", "Oxytetracycline", "Toltrazuril", "Moxidectin", "Selamectin",
)

private val wildAnalyzedNames = listOf(
    "Fenbendazole", "Pyrantel Pamoate", "Prednisolone", "Ivermectin",
    "Oxytetracycline", "Albendazole", "Levamisole", "Meloxicam",
    "Enrofloxacin", "Amoxicillin", "Doxycycline", "Ceftiofur",
    "Dexamethasone", "Metronidazole", "Praziquantel", "Furosemide",
    "Atropine / Supportive (wild)", "Topical Wound / Antiseptic Ointment (wild)",
    "Metronidazole (wild)", "Ketamine (wild)", "Xylazine (wild)",
)

/** Medication names found in findings/recommendations, known ones first. */
internal fun extractedDrugNames(text: String, dictionary: List<String>): List<String> =
    medicationMentions(text, dictionary).map { it.name }

/** Preferred domestic product for a suspected condition, if one is mapped. */
fun domesticConditionDrug(condition: String?): FormularyDrug? =
    condition?.lowercase()
        ?.let { key -> domesticConditionDrugs.entries.firstOrNull { it.key in key }?.value }
        ?.let(::findDrug)

/** Preferred wildlife product for a condition, restricted to the selected group. */
fun wildConditionDrug(condition: String?, group: WildlifeGroup): WildlifeFormularyEntry? =
    condition?.lowercase()
        ?.let { key -> wildConditionDrugs.entries.firstOrNull { it.key in key }?.value }
        ?.let(::findWildlifeDrug)
        ?.takeIf { group in it.doseByGroup }

/** Best domestic default: explicit text, then analyzed drug, then condition. */
fun bestDomesticDrug(
    text: String,
    condition: String?,
    species: Species,
): FormularyDrug? {
    val drugs = domesticDrugsFor(text)
    return matchDrugFromText(text)
        ?: drugs.firstOrNull { it.isAnalyzedFallback && species in it.doseBySpecies }
        ?: domesticConditionDrug(condition)
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
        ?: drugs.firstOrNull { it.isAnalyzedFallback && group in it.doseByGroup }
        ?: wildConditionDrug(condition, group)
        ?: drugs.firstOrNull { group in it.doseByGroup }
}

/** Full domestic picker: analyzed additions pinned above the static products. */
fun domesticDrugsFor(text: String): List<FormularyDrug> {
    val analyzed = medicationMentions(text, domesticAnalyzedNames)
        .filter { mention -> formulary.none { namesMatch(it.name, mention.name) } }
        .map(::syntheticDomesticDrug)
    return analyzed + formulary
}

/** Wildlife picker: analyzed additions pinned above the group-only products. */
fun wildDrugsFor(text: String, group: WildlifeGroup): List<WildlifeFormularyEntry> {
    val static = wildlifeDrugsFor(group)
    val analyzed = medicationMentions(text, wildAnalyzedNames)
        .filter { mention -> static.none { namesMatch(it.name, mention.name) } }
        .map { syntheticWildDrug(it, group) }
    return analyzed + static
}

private fun slug(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

private fun syntheticDomesticDrug(mention: AnalyzedMention): FormularyDrug {
    val lower = mention.name.lowercase()
    val topical = listOf("ointment", "oil", "spray", "drench").any { it in lower }
    return FormularyDrug(
        id = "analyzed-domestic-${slug(mention.name)}",
        name = "${mention.name} (analyzed — verify dose)",
        doseBySpecies = Species.entries.filter { it != Species.OTHER }
            .associateWith { mention.doseRange ?: DoseRange(5.0, 10.0) },
        concentrationsMgPerMl = listOf(50.0),
        route = mention.route ?: if (topical) DoseRoute.TOPICAL else DoseRoute.IM,
        frequency = mention.frequency.ifBlank { "Verify frequency against a current formulary" },
        meatWithdrawalDays = 0,
        milkWithdrawalDays = 0,
        isAnalyzedFallback = true,
    )
}

private fun syntheticWildDrug(
    mention: AnalyzedMention,
    group: WildlifeGroup,
): WildlifeFormularyEntry {
    val lower = mention.name.lowercase()
    val topical = listOf("ointment", "oil", "spray", "drench").any { it in lower }
    return WildlifeFormularyEntry(
        id = "analyzed-wild-${slug(mention.name)}",
        name = "${mention.name.removeSuffix(" (wild)")} (wild; analyzed — verify dose)",
        doseByGroup = WildlifeGroup.entries.associateWith {
            mention.doseRange ?: DoseRange(5.0, 10.0)
        },
        concentrationsMgPerMl = listOf(50.0),
        route = mention.route ?: if (topical) DoseRoute.TOPICAL else DoseRoute.IM,
        frequency = mention.frequency.ifBlank { "Verify frequency against a current wildlife protocol" },
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
