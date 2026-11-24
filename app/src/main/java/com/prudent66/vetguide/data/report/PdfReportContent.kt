package com.prudent66.vetguide.data.report

internal const val SUBJECTIVE_TITLE = "S — Subjective (History / Anamnesis)"
internal const val OBJECTIVE_TITLE = "O — Objective (Clinical Findings)"
internal const val ASSESSMENT_TITLE = "A — Assessment (Ranked Differentials)"
internal const val PLAN_TITLE = "P — Plan (Treatment & Supportive Care)"

/** Prescription lines detected in a plan (mg/kg dosing lines). */
internal fun planPrescriptions(data: HospitalReportData): List<String> =
    (data.prescriptions + data.soap.plan.lines().filter { it.contains("mg/kg", ignoreCase = true) })
        .distinct()
        .filter { it.isNotBlank() }

/** Supportive-care lines detected in a plan. */
internal fun planSupportiveCare(data: HospitalReportData): List<String> =
    data.supportiveCare.ifEmpty {
        data.soap.plan.lines().filterNot { it.contains("mg/kg", ignoreCase = true) }
    }

/**
 * Section headers actually rendered in the report. P is emitted exactly once,
 * immediately before the separated prescription/supportive boxes.
 */
internal fun reportSectionTitles(data: HospitalReportData): List<String> = buildList {
    if (data.soap.subjective.isNotBlank()) add(SUBJECTIVE_TITLE)
    if (data.soap.objective.isNotBlank()) add(OBJECTIVE_TITLE)
    if (data.soap.assessment.isNotBlank()) add(ASSESSMENT_TITLE)
    if (planPrescriptions(data).isNotEmpty() || planSupportiveCare(data).isNotEmpty()) {
        add(PLAN_TITLE)
    }
}
