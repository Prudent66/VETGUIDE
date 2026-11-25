package com.prudent66.vetguide.ui.components

/** Shown in the dosage panels whenever the weight field is blank or <= 0. */
internal const val ENTER_WEIGHT_MESSAGE =
    "Enter patient weight (kg) to calculate dose and syringe volume"

/** Shared empty-weight message for domestic and wildlife dosage panels. */
internal fun dosageMessage(weightMissing: Boolean, fallback: String): String =
    if (weightMissing) ENTER_WEIGHT_MESSAGE else fallback
