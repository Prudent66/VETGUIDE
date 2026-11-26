package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.WildlifeGroup

/**
 * One wildlife medication recommended by an assessment, resolved only through
 * the wildlife formulary and the selected group.
 */
data class WildMedication(
    val label: String,
    val drug: WildlifeFormularyEntry,
)

/** Every actionable wildlife medication; warning-only mentions are excluded. */
fun wildMedicationsFor(text: String, group: WildlifeGroup): List<WildMedication> =
    recommendedMedicationMentions(text, wildAnalyzedNames)
        .map { mention ->
            WildMedication(
                label = medicationLabel(mention),
                drug = wildDrugFor(mention, group),
            )
        }
        .distinctBy { it.drug.id }

/**
 * Selectable wildlife drugs after an assessment: only assessed medicines for
 * the chosen group, with a safe default (if any) first. With no assessment
 * the full standalone group formulary is kept.
 */
fun wildDrugChoices(
    text: String,
    group: WildlifeGroup,
    preferred: WildlifeFormularyEntry? = null,
): List<WildlifeFormularyEntry> {
    val assessed = wildMedicationsFor(text, group).map { it.drug }
    return if (assessed.isEmpty()) {
        wildDrugsFor(text, group)
    } else {
        (listOfNotNull(preferred) + assessed).distinctBy { it.id }
    }
}

/** True when the assessed wildlife medicines are replaceable "or" options. */
fun wildHasAlternatives(text: String): Boolean =
    hasReplaceableAlternatives(recommendedMedicationMentions(text, wildAnalyzedNames))

/** Carries the preparation site into the visible medication label. */
private fun medicationLabel(mention: AnalyzedMention): String = when {
    listOf("ophthalmic", "eye").any { it in mention.context.lowercase() } ->
        "${mention.name} (ophthalmic)"
    "wound" in mention.context.lowercase() -> "${mention.name} (wound care)"
    else -> mention.name
}

/** Never falls back to a domestic product or a group the caller did not select. */
private fun wildDrugFor(
    mention: AnalyzedMention,
    group: WildlifeGroup,
): WildlifeFormularyEntry {
    val matching = wildlifeFormulary.firstOrNull {
        namesMatch(it.name, mention.name) && group in it.doseByGroup
    } ?: matchWildlifeDrugFromText(mention.context, group)
    return if (mention.route == DoseRoute.TOPICAL) {
        syntheticWildDrug(mention, group)
    } else {
        matching ?: syntheticWildDrug(mention, group)
    }
}
