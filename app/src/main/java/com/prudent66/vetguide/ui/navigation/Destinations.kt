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
    const val HOME = "home"
    const val DIAGNOSE = "diagnose"
    const val CASES = "cases"
    const val KNOWLEDGE = "knowledge"
    const val SETTINGS = "settings"

    /** Order defines bottom-bar order: Home, Diagnose, Cases, Knowledge, Settings. */
    val items: List<VetguideDestination> = listOf(
        VetguideDestination(HOME, "Home", R.drawable.nav_home),
        VetguideDestination(DIAGNOSE, "Diagnose", R.drawable.nav_diagnose),
        VetguideDestination(CASES, "Cases", R.drawable.nav_cases),
        VetguideDestination(KNOWLEDGE, "Knowledge", R.drawable.nav_knowledge),
        VetguideDestination(SETTINGS, "Settings", R.drawable.nav_settings),
    )
}
