package com.prudent66.vetguide.ui.screens.knowledge

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.prudent66.vetguide.domain.model.KbUrgency
import com.prudent66.vetguide.domain.model.Species

/** Shared KB label helpers (kept out of screen files for readability). */

internal fun speciesLabel(species: Species?): String = species?.name
    ?.lowercase()
    ?.replaceFirstChar { it.uppercase() }
    ?: "All"

internal fun labelOf(urgency: KbUrgency?): String = when (urgency) {
    null -> "Urgency: unspecified"
    else -> "Urgency: ${urgency.name.lowercase().replaceFirstChar { it.uppercase() }}"
}

@Composable
internal fun urgencyColor(urgency: KbUrgency?) = when (urgency) {
    KbUrgency.EMERGENCY -> MaterialTheme.colorScheme.error
    KbUrgency.URGENT -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
