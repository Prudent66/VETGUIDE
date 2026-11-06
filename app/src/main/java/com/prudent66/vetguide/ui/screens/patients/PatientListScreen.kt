package com.prudent66.vetguide.ui.screens.patients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.ui.viewmodels.PatientListUiState
import com.prudent66.vetguide.ui.viewmodels.PatientViewModel

/**
 * Patient management gateway (Phase 1B). Lists Name / Species / Breed with an
 * empty state and a FAB that opens the Add Patient form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    onPatientClick: (String) -> Unit,
    onAddPatientClick: () -> Unit,
    viewModel: PatientViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Patients") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPatientClick) {
                Icon(
                    painter = painterResource(R.drawable.nav_add),
                    contentDescription = "Add patient",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                PatientListUiState.Loading -> LoadingBox()
                is PatientListUiState.Success ->
                    if (s.patients.isEmpty()) {
                        EmptyState()
                    } else {
                        PatientList(
                            patients = s.patients,
                            modifier = Modifier.fillMaxSize(),
                            onPatientClick = onPatientClick,
                        )
                    }
                is PatientListUiState.Error -> ErrorBox(s.message)
            }
        }
    }
}

@Composable
private fun PatientList(
    patients: List<Patient>,
    modifier: Modifier,
    onPatientClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(patients, key = { it.id }) { patient ->
            PatientRow(patient = patient, onPatientClick = onPatientClick)
        }
    }
}

@Composable
private fun PatientRow(patient: Patient, onPatientClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPatientClick(patient.id) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(patient.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${patient.species.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                    (patient.breed?.let { " · $it" } ?: " · Breed unknown"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.nav_cases),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("No patients yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add your first patient with the + button",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
