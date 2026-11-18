package com.prudent66.vetguide.domain.model

/**
 * Age units for the patient form: poultry and young animals are measured in
 * days or weeks, adults in months or years. Stored by name in Room.
 */
enum class AgeUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS;

    /** Display label ("Days", "Weeks", …). */
    val label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/** "3 Days", "1.5 Years" — the field phrasing used in cards, lists and PDFs. */
fun formatAge(value: Double?, unit: AgeUnit?): String? {
    if (value == null || unit == null || value <= 0.0) return null
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return "$number ${unit.label}"
}

/** Formatted age for display / AI context; null when the age is unknown. */
fun Patient.ageLabel(): String? = formatAge(ageValue, ageUnit)
