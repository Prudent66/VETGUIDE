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
    "skin infection" to "topical-antibiotic-ointment",
    "skin lesion" to "topical-antibiotic-ointment",
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

internal val domesticAnalyzedNames = listOf(
    "Amoxicillin LA", "Amoxicillin", "Doxycycline", "Gentamicin", "Ceftiofur",
    "Flunixin Meglumine", "Calcium Borogluconate", "Vitamin B-Complex",
    "Topical Antibiotic Ointment", "Wound Spray / Antiseptic Oil",
    "Metronidazole", "Praziquantel", "Fenbendazole", "Dexamethasone",
    "Sulfadimidine", "Trimethoprim", "Levamisole", "Closantel",
    "Doramectin", "Furosemide", "Prednisolone", "Pyrantel Pamoate",
    "Ivermectin", "Oxytetracycline", "Toltrazuril", "Moxidectin", "Selamectin",
    "Neosporin", "Bacitracin", "Polysporin", "Triple Antibiotic Ointment",
    "Mupirocin", "Flurbiprofen", "Meloxicam", "Neomycin", "Miconazole", "Clotrimazole",
    "Ketoconazole", "Fluconazole", "Nystatin", "Griseofulvin", "Albendazole",
    "Enrofloxacin", "Amprolium", "Tylosin", "Diminazene Aceturate",
    "Penicillin-Streptomycin", "Florfenicol (Aquatic)", "Florfenicol",
)

internal val wildAnalyzedNames = listOf(
    "Fenbendazole", "Pyrantel Pamoate", "Prednisolone", "Ivermectin",
    "Oxytetracycline", "Albendazole", "Levamisole", "Meloxicam",
    "Enrofloxacin", "Amoxicillin", "Doxycycline", "Ceftiofur",
    "Dexamethasone", "Metronidazole", "Praziquantel", "Furosemide", "Flurbiprofen",
    "Atropine / Supportive (wild)", "Topical Wound / Antiseptic Ointment (wild)",
    "Metronidazole (wild)", "Ketamine (wild)", "Xylazine (wild)",
    "Neomycin", "Bacitracin", "Miconazole", "Clotrimazole", "Ketoconazole",
    "Fluconazole", "Nystatin", "Griseofulvin", "Amprolium", "Tylosin",
    "Diminazene", "Neosporin", "Doramectin",
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

/**
 * Best domestic default with the same analysed-first flow as wildlife. The
 * text matcher is consulted only after an analysed medication or a condition
 * recommendation, so an incidental oxytetracycline keyword can never become
 * an unrequested IM default. A known topical still wins over its analysed
 * counterpart so the application guidance comes from the formulary.
 */
fun bestDomesticDrug(
    text: String,
    condition: String?,
    species: Species,
): FormularyDrug? {
    val drugs = domesticDrugsFor(text)
    val analyzed = drugs.firstOrNull { it.isAnalyzedFallback && species in it.doseBySpecies }
    val static = matchDrugFromText(actionableClauses(text).joinToString(" "))
    val conditionMatch = domesticConditionDrug(condition)
    val selected = when {
        static?.route == DoseRoute.TOPICAL && analyzed?.route == DoseRoute.TOPICAL -> static
        analyzed != null -> analyzed
        conditionMatch != null && (static == null || static.id.startsWith("oxytetracycline")) ->
            conditionMatch
        static != null -> static
        else -> drugs.firstOrNull { species in it.doseBySpecies }
    }
    val mention = recommendedMedicationMentions(text, domesticAnalyzedNames)
        .firstOrNull { m -> selected?.let { drug -> namesMatch(drug.name, m.name) } == true }
    return selected?.let { drug -> AdministrationRoute.apply(drug, mention) }
}

/** Best wildlife default, using only the independent wildlife formulary. */
fun bestWildDrug(
    text: String,
    condition: String?,
    group: WildlifeGroup,
): WildlifeFormularyEntry? {
    val drugs = wildDrugsFor(text, group)
    val selected = matchWildlifeDrugFromText(actionableClauses(text).joinToString(" "), group)
        ?: drugs.firstOrNull { it.isAnalyzedFallback && group in it.doseByGroup }
        ?: wildConditionDrug(condition, group)
        ?: drugs.firstOrNull { group in it.doseByGroup }
    val mention = recommendedMedicationMentions(text, wildAnalyzedNames)
        .firstOrNull { m -> selected?.let { drug -> namesMatch(drug.name, m.name) } == true }
    return selected?.let { drug -> AdministrationRoute.apply(drug, mention) }
}

/** Normalized names that appear only in warning clauses, so must not be offered. */
private fun warnedNames(text: String, dictionary: List<String>): Set<String> {
    val all = medicationMentions(text, dictionary)
        .map { normalizedDrugName(it.name) }.toSet()
    val recommended = recommendedMedicationMentions(text, dictionary)
        .map { normalizedDrugName(it.name) }.toSet()
    return all - recommended
}

/** Full domestic picker: analyzed additions pinned above the static products. */
fun domesticDrugsFor(text: String): List<FormularyDrug> {
    val warned = warnedNames(text, domesticAnalyzedNames + formulary.map { it.name })
    val analyzed = recommendedMedicationMentions(text, domesticAnalyzedNames)
        .filter { mention -> formulary.none { namesMatch(it.name, mention.name) } }
        .map(::syntheticDomesticDrug)
    val statics = formulary.filterNot { normalizedDrugName(it.name) in warned }
    return (analyzed + statics).distinctBy { it.id }
}

/** Wildlife picker: analyzed additions pinned above the group-only products. */
fun wildDrugsFor(text: String, group: WildlifeGroup): List<WildlifeFormularyEntry> {
    val static = wildlifeDrugsFor(group)
    val warned = warnedNames(text, wildAnalyzedNames + static.map { it.name })
    val analyzed = recommendedMedicationMentions(text, wildAnalyzedNames)
        .filter { mention -> static.none { namesMatch(it.name, mention.name) } }
        .map { syntheticWildDrug(it, group) }
    val available = static.filterNot { normalizedDrugName(it.name) in warned }
    return (analyzed + available).distinctBy { it.id }
}

private val idSlugRegex = Regex("[^a-z0-9]+")

internal fun syntheticDomesticDrug(mention: AnalyzedMention): FormularyDrug {
    val lower = mention.name.lowercase()
    val topical = listOf("ointment", "oil", "spray", "drench").any { it in lower }
    return FormularyDrug(
        id = "analyzed-domestic-" + lower.replace(idSlugRegex, "-").trim('-'),
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

internal fun syntheticWildDrug(
    mention: AnalyzedMention,
    group: WildlifeGroup,
): WildlifeFormularyEntry {
    val lower = mention.name.lowercase()
    val topical = listOf("ointment", "oil", "spray", "drench").any { it in lower }
    return WildlifeFormularyEntry(
        id = "analyzed-wild-" + lower.replace(idSlugRegex, "-").trim('-'),
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
