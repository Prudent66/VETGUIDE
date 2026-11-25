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

/** Shown in the dosage panels whenever the weight field is blank or <= 0. */
internal const val ENTER_WEIGHT_MESSAGE =
    "Enter patient weight (kg) to calculate dose and syringe volume"

/** Shared empty-weight message for domestic and wildlife dosage panels. */
internal fun dosageMessage(weightMissing: Boolean, fallback: String): String =
    if (weightMissing) ENTER_WEIGHT_MESSAGE else fallback
