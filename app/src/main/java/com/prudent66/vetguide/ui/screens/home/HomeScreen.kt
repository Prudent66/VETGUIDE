package com.prudent66.vetguide.ui.screens.home
import com.prudent66.vetguide.ui.components.LocalBottomBarClearance

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
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.ui.viewmodels.HomeViewModel
import com.prudent66.vetguide.ui.viewmodels.RecentCaseSummary

/**
 * Home: VETGUIDE hub. The most recent patient and case are tappable shortcuts
 * into the record screens; the diary note is a clinical-safety reminder per
 * roadmap §10.
 */
@Composable
fun HomeScreen(
    onPatientClick: (String) -> Unit = {},
    onCaseClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recentPatient by viewModel.recentPatient.collectAsState()
    val recentCase by viewModel.recentCase.collectAsState()
    val patientCount by viewModel.patientCount.collectAsState()
    val conditionCount by viewModel.conditionCount.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = LocalBottomBarClearance.current),
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
        WelcomeCard(patientCount = patientCount, conditionCount = conditionCount)
        RecentPatientCard(
            patient = recentPatient,
            onOpen = { id -> onPatientClick(id) },
        )
        recentCase?.let { recent ->
            RecentCaseCard(recent = recent, onOpen = { onCaseClick(recent.caseId) })
        }
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeCard(patientCount: Int?, conditionCount: Int?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Welcome", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Offline-first decision support for dogs, cats, and regional " +
                    "livestock — questionnaires, a cited knowledge base, and clinical " +
                    "records for your practice.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = statsLine(patientCount, conditionCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RecentPatientCard(patient: Patient?, onOpen: (String) -> Unit) {
    val clickModifier = if (patient == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(patient.id) }
    }
    Card(modifier = clickModifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Most recent patient", style = MaterialTheme.typography.titleSmall)
            if (patient == null) {
                Text(
                    text = "No patients yet — add your first from the Patients tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(patient.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = patientSubtitle(patient),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentCaseCard(recent: RecentCaseSummary, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Most recent case", style = MaterialTheme.typography.titleSmall)
            Text(recent.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${recent.patientName} · ${recent.status.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun patientSubtitle(patient: Patient): String {
    val species = patient.species.name.lowercase().replaceFirstChar { it.uppercase() }
    val breed = patient.breed?.takeIf { it.isNotBlank() } ?: "Breed unknown"
    return "$species · $breed"
}

private fun statsLine(patientCount: Int?, conditionCount: Int?): String {
    val patients = patientCount?.let { "$it patient${if (it == 1) "" else "s"}" } ?: "—"
    val references = conditionCount?.let { "$it condition${if (it == 1) "" else "s"}" } ?: "—"
    return "$patients registered · $references in the knowledge base"
}
