package com.prudent66.vetguide.domain.formulary

/**
 * One domestic medication recommended by an assessment, resolved only through
 * the domestic formulary. [label] keeps the ophthalmic/wound context that the
 * formulary product name alone does not carry.
 */
data class DomesticMedication(
    val label: String,
    val drug: FormularyDrug,
)

/** Every actionable domestic medication; warning-only mentions are excluded. */
fun domesticMedicationsFor(text: String): List<DomesticMedication> =
    recommendedMedicationMentions(text, domesticAnalyzedNames)
        .map { mention ->
            DomesticMedication(
                label = medicationLabel(mention),
                drug = domesticDrugFor(mention),
            )
        }
        .distinctBy { it.drug.id }

/** Carries the preparation site into the visible medication label. */
private fun medicationLabel(mention: AnalyzedMention): String = when {
    listOf("ophthalmic", "eye").any { it in mention.context.lowercase() } ->
        "${mention.name} (ophthalmic)"
    "wound" in mention.context.lowercase() -> "${mention.name} (wound care)"
    else -> mention.name
}

/** Topicals keep their preparation-specific synthetic product, not an IM default. */
private fun domesticDrugFor(mention: AnalyzedMention): FormularyDrug {
    val matching = matchDrugFromText(mention.context)
        ?: formulary.firstOrNull { namesMatch(it.name, mention.name) }
    return if (mention.route == DoseRoute.TOPICAL) {
        syntheticDomesticDrug(mention)
    } else {
        matching ?: syntheticDomesticDrug(mention)
    }
}
