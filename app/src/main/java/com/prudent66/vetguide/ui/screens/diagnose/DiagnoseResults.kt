package com.prudent66.vetguide.ui.screens.diagnose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.model.DiagnosticMatch
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.screens.knowledge.labelOf
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel
import com.prudent66.vetguide.ui.screens.knowledge.urgencyColor
import com.prudent66.vetguide.ui.viewmodels.DiagnosticUiState

/** Ranked placeholder differentials ("Match Strength") + urgency + review notice. */
@Composable
fun ResultsPhase(
    results: DiagnosticUiState.Results,
    onOpenCondition: (String) -> Unit,
    onRestart: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ReviewBanner(results.species) }
        if (results.matches.isEmpty()) {
            item { EmptyDifferentials() }
        } else {
            items(results.matches, key = { it.conditionId }) { match ->
                MatchCard(
                    match = match,
                    onOpenCondition = { onOpenCondition(match.conditionId) },
                )
            }
        }
        item {
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("New assessment") }
        }
    }
}

@Composable
private fun ReviewBanner(species: Species?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Placeholder diagnostic engine — requires veterinary review.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = species?.let { "Species assessed: ${speciesLabel(it)}." }
                    ?: "Species: unspecified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Match Strength values come from dummy placeholder weights and " +
                    "must not guide treatment decisions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDifferentials() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No ranked differentials from the placeholder seed for the selected " +
                "signs. Record the case and review with a veterinarian.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MatchCard(
    match: DiagnosticMatch,
    onOpenCondition: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenCondition() },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(match.conditionName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Match Strength: ${match.matchStrengthPercent}%",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = labelOf(match.urgency),
                style = MaterialTheme.typography.labelMedium,
                color = urgencyColor(match.urgency),
            )
            Text(
                text = "Signs matched: ${match.matchedSigns} of ${match.totalSigns} " +
                    "(placeholder weights)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
