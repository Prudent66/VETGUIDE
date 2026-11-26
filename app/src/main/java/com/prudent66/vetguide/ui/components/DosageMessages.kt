package com.prudent66.vetguide.ui.components

import com.prudent66.vetguide.domain.formulary.DoseRoute

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

/** Application instructions for ointments, oils, sprays and eye preparations. */
internal fun topicalGuidance(route: DoseRoute, name: String = ""): String? =
    if (route == DoseRoute.TOPICAL) {
        if (listOf("ophthalmic", "eye").any { it in name.lowercase() }) {
            "Ophthalmic preparation — apply the calculated amount to the affected eye " +
                "as directed. Do not draw this preparation into a syringe."
        } else {
            "Topical use — clean and dry the area, then apply a thin layer. The calculated " +
                "amount is an estimate; adjust to the treated area and verify the product with " +
                "a veterinary professional."
        }
    } else {
        null
    }

/** Shown in the dosage panels whenever the weight field is blank or <= 0. */
internal const val ENTER_WEIGHT_MESSAGE =
    "Enter patient weight (kg) to calculate dose and syringe volume"

/** Shared empty-weight message for domestic and wildlife dosage panels. */
internal fun dosageMessage(weightMissing: Boolean, fallback: String): String =
    if (weightMissing) ENTER_WEIGHT_MESSAGE else fallback
