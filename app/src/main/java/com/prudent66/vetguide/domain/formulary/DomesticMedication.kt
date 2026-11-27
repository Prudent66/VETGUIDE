package com.prudent66.vetguide.domain.formulary

/**
 * One domestic medication recommended by an assessment, resolved only through
 * the domestic formulary. [label] keeps the ophthalmic/wound context that the
 * formulary product name alone does not carry; [ocular] is true only when eye
 * involvement was stated in the assessment.
 */
data class DomesticMedication(
    val label: String,
    val drug: FormularyDrug,
    val ocular: Boolean = false,
)

/** Every actionable domestic medication; warning-only mentions are excluded. */
fun domesticMedicationsFor(text: String): List<DomesticMedication> =
    recommendedMedicationMentions(text, domesticAnalyzedNames)
        .map { mention ->
            DomesticMedication(
                label = medicationLabel(mention),
                drug = domesticDrugFor(mention),
                ocular = AdministrationRoute.ocular(mention.context),
            )
        }
        .distinctBy { it.drug.id }

/**
 * Selectable drugs after an assessment: the assessed medicines for this
 * pathway, with a safe default (if any) first. With no assessment the full
 * standalone formulary is kept so the plain calculator is unchanged.
 */
fun domesticDrugChoices(
    text: String,
    preferred: FormularyDrug? = null,
): List<FormularyDrug> {
    val assessed = domesticMedicationsFor(text).map { it.drug }
    return if (assessed.isEmpty()) {
        domesticDrugsFor(text)
    } else {
        (listOfNotNull(preferred) + assessed).distinctBy { it.id }
    }
}

/** True when the assessed domestic medicines are replaceable "or" options. */
fun domesticHasAlternatives(text: String): Boolean =
    hasReplaceableAlternatives(recommendedMedicationMentions(text, domesticAnalyzedNames))

/** Carries an explicitly stated preparation site into the visible label. */
private fun medicationLabel(mention: AnalyzedMention): String = when {
    AdministrationRoute.ocular(mention.context) -> "${mention.name} (ophthalmic)"
    "wound" in mention.context.lowercase() ||
        "lesion" in mention.context.lowercase() -> "${mention.name} (wound care)"
    else -> mention.name
}

/** Topicals keep their preparation-specific synthetic product, not an IM default. */
private fun domesticDrugFor(mention: AnalyzedMention): FormularyDrug {
    val matching = matchDrugFromText(mention.context)
        ?: formulary.firstOrNull { namesMatch(it.name, mention.name) }
    return if (mention.route == DoseRoute.TOPICAL) {
        syntheticDomesticDrug(mention)
    } else {
        AdministrationRoute.apply(matching ?: syntheticDomesticDrug(mention), mention)
    }
}
