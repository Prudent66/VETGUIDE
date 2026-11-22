package com.prudent66.vetguide.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Stable resting clearance for the collapsing copyright/navigation bar.
 * Provided once by [com.prudent66.vetguide.VetguideApp] and consumed inside
 * each screen's scrollable container so content can scroll flush down to the
 * footer while the last row/button still clears it when scrolling stops.
 */
@Suppress("TopLevelPropertyNaming") // Compose CompositionLocal convention is PascalCase.
val LocalBottomBarClearance = compositionLocalOf { 16.dp }
