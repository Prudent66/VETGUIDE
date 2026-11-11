package com.prudent66.vetguide.ui.screens.home

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

/**
 * Home: VETGUIDE hub. Navigation lives in the bottom bar, so the screen is a
 * clean dashboard: a welcome message, live practice stats, and the most
 * recently registered patient. The diary note is a clinical-safety reminder
 * per roadmap §10.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recentPatient by viewModel.recentPatient.collectAsState()
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
        WelcomeCard(patientCount = patientCount, conditionCount = conditionCount)
        RecentPatientCard(patient = recentPatient)
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
                text = "Offline-first decision support for dogs, cats, and Nigerian " +
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
private fun RecentPatientCard(patient: Patient?) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
