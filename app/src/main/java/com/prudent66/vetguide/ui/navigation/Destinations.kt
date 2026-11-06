package com.prudent66.vetguide.ui.navigation

import androidx.annotation.DrawableRes
import com.prudent66.vetguide.R

/**
 * Top-level destinations rendered in the bottom navigation bar.
 * Routes are stable strings so screens can navigate without knowledge of graphs.
 */
data class VetguideDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int,
)

object Destinations {
    const val ARG_PATIENT_ID = "patientId"

    /** Bottom-bar tabs (top-level). The former "Cases" tab is now the Patients gateway. */
    const val HOME = "home"
    const val DIAGNOSE = "diagnose"
    const val PATIENTS = "patients"
    const val KNOWLEDGE = "knowledge"
    const val SETTINGS = "settings"

    /** Drill-in routes (no bottom bar). */
    const val PATIENTS_ADD = "patients/new"
    const val CASES_FOR_PATIENT = "patients/{$ARG_PATIENT_ID}"

    /** Route for the case timeline of a concrete patient. */
    fun casesForPatient(patientId: String): String = "patients/$patientId"

    /** Order defines bottom-bar order: Home, Diagnose, Patients, Knowledge, Settings. */
    val items: List<VetguideDestination> = listOf(
        VetguideDestination(HOME, "Home", R.drawable.nav_home),
        VetguideDestination(DIAGNOSE, "Diagnose", R.drawable.nav_diagnose),
        VetguideDestination(PATIENTS, "Patients", R.drawable.nav_cases),
        VetguideDestination(KNOWLEDGE, "Knowledge", R.drawable.nav_knowledge),
        VetguideDestination(SETTINGS, "Settings", R.drawable.nav_settings),
    )

    fun isTopLevel(route: String?): Boolean = items.any { it.route == route }
}
