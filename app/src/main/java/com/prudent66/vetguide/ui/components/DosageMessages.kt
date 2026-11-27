package com.prudent66.vetguide.ui.components

import com.prudent66.vetguide.domain.formulary.DoseRange
import com.prudent66.vetguide.domain.formulary.DoseRoute
import com.prudent66.vetguide.domain.formulary.DosageResult
import com.prudent66.vetguide.domain.formulary.FormularyDrug
import com.prudent66.vetguide.domain.formulary.WildlifeDosageResult
import com.prudent66.vetguide.domain.formulary.WildlifeFormularyEntry
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup

/** Topicals describe an application amount rather than a syringe draw. */
internal fun volumeLabel(route: DoseRoute): String = if (route == DoseRoute.TOPICAL) {
    "Application Amount (mL)"
} else {
    "Syringe Volume to Draw (mL)"
}

/** Flag shown when an analyzed drug is supplied with conservative fallback dosing. */
internal const val ANALYZED_DRUG_NOTE =
    "Analyzed drug — fallback dose only; confirm before use."

/** Shown once when assessed medicines are interchangeable "or" options. */
internal const val REPLACEABLE_ALTERNATIVES_NOTE =
    "The listed options are replaceable alternatives, not medicines to give together. " +
        "Choose one option and dose it against the current Weight (kg); " +
        "confirm the dose before use."

/**
 * Application instructions for ointments, oils and sprays. Eye wording is
 * used only when the assessment explicitly stated ocular involvement; skin,
 * coat and body-surface lesions keep their topical guidance.
 */
internal fun topicalGuidance(route: DoseRoute, ocular: Boolean = false): String? =
    if (route == DoseRoute.TOPICAL) {
        if (ocular) {
            "Ophthalmic preparation — apply the calculated amount to the affected eye " +
                "as directed. Do not draw this preparation into a syringe."
        } else {
            "Topical use — clean and dry the skin or lesion, then apply a thin layer. " +
                "The calculated amount is an estimate; adjust to the treated area and " +
                "verify the product with a veterinary professional."
        }
    } else {
        null
    }

/** Reminds the reader that free-text dose numbers do not drive the calculation. */
internal const val RESOLVED_DOSE_PRECEDENCE_NOTE =
    "Calculated values below use the resolved formulary or plan dose rule. " +
        "Where free-text numbers above differ, verify the dose before use."

/** One dose narrative built from the very result the numeric lines display. */
private fun doseNoteText(
    range: DoseRange,
    selected: Double,
    doseMg: Double,
    amountMl: Double,
    concentration: Double,
    route: DoseRoute,
): String =
    "Calculated at ${formatAmount(selected)} mg/kg " +
        "(range ${formatAmount(range.minMgPerKg)}–${formatAmount(range.maxMgPerKg)} mg/kg): " +
        "${formatAmount(doseMg)} mg gives ${formatAmount(amountMl)} mL from a " +
        "${formatAmount(concentration)} mg/mL bottle by ${route.label}. " +
        "Verify against a current formulary before use."

/** Domestic note derived from the already-calculated result. */
internal fun domesticDoseNote(result: DosageResult): String = doseNoteText(
    result.doseRange, result.selectedMgPerKg, result.doseMg, result.syringeMl,
    result.concentrationMgPerMl, result.route,
)

/** Wildlife note derived from the already-calculated result. */
internal fun wildDoseNote(result: WildlifeDosageResult): String = doseNoteText(
    result.doseRange, result.selectedMgPerKg, result.doseMg, result.syringeMl,
    result.concentrationMgPerMl, result.route,
)

/** Actionable explanation for a missing species/route/concentration preset. */
internal fun doseFallbackMessage(
    name: String,
    subject: String,
    route: DoseRoute,
    concentration: Double,
    presetAvailable: Boolean,
): String = when {
    concentration <= 0.0 ->
        "No usable bottle concentration is set for $name ($concentration mg/mL). " +
            "Select a valid concentration or verify the product strength before dosing."
    !presetAvailable ->
        "No preset mg/kg rule exists for $name in $subject by ${route.label}. " +
            "Choose another species, drug or route with a preset, or verify a " +
            "$subject-specific mg/kg dose in a current formulary and adjust manually."
    else ->
        "No calculated dose is available for $name in $subject at " +
            "${formatAmount(concentration)} mg/mL by ${route.label}. " +
            "Recheck the weight and bottle concentration, or verify the dose manually " +
            "in a current formulary."
}

/** Fallback for a domestic manual-picker selection. */
internal fun domesticDoseFallback(
    drug: FormularyDrug,
    species: Species,
    route: DoseRoute,
    concentration: Double,
): String = doseFallbackMessage(
    drug.name, ClinicalSpeciesCatalog.label(species), route, concentration,
    species in drug.doseBySpecies && route == drug.route,
)

/** Fallback for a wildlife manual-picker selection. */
internal fun wildDoseFallback(
    drug: WildlifeFormularyEntry,
    group: WildlifeGroup,
    route: DoseRoute,
    concentration: Double,
): String = doseFallbackMessage(
    drug.name, group.label, route, concentration,
    group in drug.doseByGroup && route == drug.route,
)

/** Shown in the dosage panels whenever the weight field is blank or <= 0. */
internal const val ENTER_WEIGHT_MESSAGE =
    "Enter patient weight (kg) to calculate dose and syringe volume"

/** Shared empty-weight message for domestic and wildlife dosage panels. */
internal fun dosageMessage(weightMissing: Boolean, fallback: String): String =
    if (weightMissing) ENTER_WEIGHT_MESSAGE else fallback
