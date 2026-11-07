package com.prudent66.vetguide.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.ui.navigation.Destinations
import com.prudent66.vetguide.ui.viewmodels.HomeViewModel

/**
 * Home: VETGUIDE hub. Cards carry live data (patient / condition counts) and
 * act as shortcuts into the corresponding top-level tabs. The diary note is a
 * clinical-safety reminder per roadmap §10.
 */
@Composable
fun HomeScreen(
    onOpenDestination: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val patientCount by viewModel.patientCount.collectAsState()
    val conditionCount by viewModel.conditionCount.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "VETGUIDE",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        FeatureCard(
            title = "Diagnose",
            subtitle = "Start a new guided investigation",
            onClick = { onOpenDestination(Destinations.DIAGNOSE) },
        )
        FeatureCard(
            title = "Cases",
            subtitle = formatCasesSubtitle(patientCount),
            onClick = { onOpenDestination(Destinations.PATIENTS) },
        )
        FeatureCard(
            title = "Knowledge Base",
            subtitle = formatKbSubtitle(conditionCount),
            onClick = { onOpenDestination(Destinations.KNOWLEDGE) },
        )
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCasesSubtitle(count: Int?): String = when {
    count == null -> "Loading patients…"
    count == 0 -> "No patients yet"
    else -> "$count Patients Registered"
}

private fun formatKbSubtitle(count: Int?): String = when {
    count == null -> "Loading references…"
    count == 0 -> "No clinical references yet"
    else -> "Search $count Clinical References"
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
