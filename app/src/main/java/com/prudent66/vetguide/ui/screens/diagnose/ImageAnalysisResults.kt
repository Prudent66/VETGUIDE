package com.prudent66.vetguide.ui.screens.diagnose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.ai.OFFLINE_PROVIDER_ID
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel
import com.prudent66.vetguide.ui.viewmodels.ImageAnalysisUiState

/**
 * Phase 6 (§4.2) analyzing + result phases. Provenance is always surfaced
 * (§5.2/§10): a provider id means "AI prediction" with the provider named;
 * [OFFLINE_PROVIDER_ID] means deterministic rule-based output and is
 * visually distinguished (§10) — it is never presented as AI.
 */
@Composable
fun ImageAnalyzingPhase(analysis: ImageAnalysisUiState.Analyzing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Analyzing image…", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "This may take up to 60 seconds depending on network speed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = speciesLine(analysis.species),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ImageResultPhase(
    result: ImageAnalysisUiState.Result,
    onRestart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProvenanceBanner(result)
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = result.narrative,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        DiagnosticsCard(result.diagnostics)
        Text(
            text = "This output is decision support only — not a diagnosis. The " +
                "attending veterinarian remains responsible for all clinical decisions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Text("New assessment")
        }
    }
}

/** Raw failure reasons for support/debugging (empty on a provider success). */
@Composable
private fun DiagnosticsCard(diagnostics: List<String>) {
    if (diagnostics.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            diagnostics.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProvenanceBanner(result: ImageAnalysisUiState.Result) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (result.providerId == OFFLINE_PROVIDER_ID) {
                        "Rule-based result"
                    } else {
                        "AI prediction"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (result.providerId == OFFLINE_PROVIDER_ID) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = "provider: ${result.providerId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = speciesLine(result.species),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.degraded) {
                Text(
                    text = "Reduced reliability: this result came from a degraded tier.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun speciesLine(species: Species?): String = when (species) {
    null -> "Species: unspecified"
    else -> "Species: ${speciesLabel(species)}"
}

/** Corrupt/unsupported payload: explicit error + Retake / Cancel (§13). */
@Composable
fun ImageErrorPhase(
    error: ImageAnalysisUiState.ImageError,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Image analysis failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(error.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Text("Retake")
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
