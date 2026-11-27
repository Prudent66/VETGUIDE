package com.prudent66.vetguide.ui.screens.patients
import com.prudent66.vetguide.ui.components.LocalBottomBarClearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.prudent66.vetguide.ui.components.StableBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.ageLabel
import com.prudent66.vetguide.ui.viewmodels.PatientListUiState
import com.prudent66.vetguide.ui.viewmodels.PatientViewModel

/**
 * Patient management gateway (Phase 1B + Phase 8 CRUD). Lists Name / Species /
 * Breed; tapping opens the patient's cases. Each row also exposes Edit and
 * Delete (with a cascade confirmation) from its overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    onPatientClick: (String) -> Unit,
    onAddPatientClick: () -> Unit,
    onEditPatientClick: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Patient?>(null) }
    var showHerd by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Patients") }) },
        floatingActionButton = { AddPatientFab(onClick = onAddPatientClick) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedButton(
                onClick = { showHerd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text("Herd / Flock Mode") }
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                PatientStateContent(
                    state = state,
                    onPatientClick = onPatientClick,
                    onEditPatientClick = onEditPatientClick,
                    onDeletePatient = { pendingDelete = it },
                )
            }
        }
    }

    if (showHerd) {
        StableBottomSheet(onDismiss = { showHerd = false }) {
            HerdOutbreakScreen(onDismiss = { showHerd = false })
        }
    }

    pendingDelete?.let { patient ->
        DeletePatientDialog(
            patient = patient,
            onConfirm = {
                viewModel.deletePatient(patient.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}


@Composable
private fun AddPatientFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        // NavHost runs flush to the footer, so lift the FAB above it.
        modifier = Modifier.padding(bottom = LocalBottomBarClearance.current),
    ) {
        Icon(
            painter = painterResource(R.drawable.nav_add),
            contentDescription = "Add patient",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PatientStateContent(
    state: PatientListUiState,
    onPatientClick: (String) -> Unit,
    onEditPatientClick: (String) -> Unit,
    onDeletePatient: (Patient) -> Unit,
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
                    onEditPatient = onEditPatientClick,
                    onDeletePatient = onDeletePatient,
                )
            }
        is PatientListUiState.Error -> ErrorBox(s.message)
    }
}

@Composable
private fun DeletePatientDialog(
    patient: Patient,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${patient.name}?") },
        text = { Text("Delete patient and all associated cases?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PatientList(
    patients: List<Patient>,
    modifier: Modifier,
    onPatientClick: (String) -> Unit,
    onEditPatient: (String) -> Unit,
    onDeletePatient: (Patient) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp + LocalBottomBarClearance.current,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(patients, key = { it.id }) { patient ->
            PatientRow(
                patient = patient,
                onPatientClick = onPatientClick,
                onEditPatient = onEditPatient,
                onDeletePatient = onDeletePatient,
            )
        }
    }
}

@Composable
private fun PatientRow(
    patient: Patient,
    onPatientClick: (String) -> Unit,
    onEditPatient: (String) -> Unit,
    onDeletePatient: (Patient) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPatientClick(patient.id) },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(patient.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        ClinicalSpeciesCatalog.display(patient),
                        patient.breed?.takeIf { it.isNotBlank() } ?: "Breed unknown",
                        patient.ageLabel(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.nav_more),
                        contentDescription = "Patient actions",
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit patient") },
                        onClick = {
                            menuOpen = false
                            onEditPatient(patient.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete patient") },
                        onClick = {
                            menuOpen = false
                            onDeletePatient(patient)
                        },
                    )
                }
            }
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
