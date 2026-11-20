package com.prudent66.vetguide.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.R

/**
 * Settings: the standing clinical disclaimer plus a note that cloud AI
 * analysis is always attempted when connectivity allows. The old opt-in
 * toggle was removed so a stale preference can never disable cloud analysis;
 * offline devices still fall back to deterministic results automatically.
 */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Cloud AI Analysis is always on",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "AI-assisted analysis is used automatically whenever " +
                "connectivity allows. When offline, the app falls back to " +
                "deterministic questionnaire and offline knowledge-base results.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
