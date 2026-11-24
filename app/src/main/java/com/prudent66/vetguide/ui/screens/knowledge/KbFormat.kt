package com.prudent66.vetguide.ui.screens.knowledge

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.KbUrgency
import com.prudent66.vetguide.domain.model.Species

/** Shared KB label helpers (kept out of screen files for readability). */

internal fun speciesLabel(species: Species?): String = species?.name
    ?.lowercase()
    ?.replaceFirstChar { it.uppercase() }
    ?: "All"

/** Human-readable label for a raw KB species tag (domestic or wildlife group). */
internal fun tagLabel(tag: String): String =
    ClinicalSpeciesCatalog.wildlifeGroup(tag)?.label
        ?: Species.entries.firstOrNull { it.name.equals(tag, ignoreCase = true) }
            ?.let { speciesLabel(it) }
        ?: tag.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

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
