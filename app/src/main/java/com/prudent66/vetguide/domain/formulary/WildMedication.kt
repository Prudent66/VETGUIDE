package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.WildlifeGroup

/**
 * One wildlife medication recommended by an assessment, resolved only through
 * the wildlife formulary and the selected group. [ocular] is true only for an
 * explicitly stated eye preparation or ocular finding.
 */
data class WildMedication(
    val label: String,
    val drug: WildlifeFormularyEntry,
    val ocular: Boolean = false,
)

/** Every actionable wildlife medication; warning-only mentions are excluded. */
fun wildMedicationsFor(text: String, group: WildlifeGroup): List<WildMedication> =
    recommendedMedicationMentions(text, wildAnalyzedNames)
        .map { mention ->
            val ocular = AdministrationRoute.ocular(AdministrationRoute.ocularEvidence(mention))
            WildMedication(
                label = medicationLabel(mention, ocular),
                drug = wildDrugFor(mention, group),
                ocular = ocular,
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

/** Carries an explicitly stated preparation site into the visible label. */
private fun medicationLabel(mention: AnalyzedMention, ocular: Boolean): String = when {
    ocular -> "${mention.name} (ophthalmic)"
    "wound" in mention.context.lowercase() ||
        "lesion" in mention.context.lowercase() -> "${mention.name} (wound care)"
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
        AdministrationRoute.apply(matching ?: syntheticWildDrug(mention, group), mention)
    }
}
