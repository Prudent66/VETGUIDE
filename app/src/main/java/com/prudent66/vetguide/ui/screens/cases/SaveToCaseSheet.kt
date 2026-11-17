package com.prudent66.vetguide.ui.screens.cases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.components.SpeciesField
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel
import com.prudent66.vetguide.ui.viewmodels.SaveToCaseUiState

/**
 * Phase 7 "Save to Case" sheet: pick an existing patient or quickly add one
 * (name + species; the rest of the signalment stays editable on the patient).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToCaseSheet(
    state: SaveToCaseUiState.Visible,
    onSelectPatient: (String?) -> Unit,
    onQuickAddName: (String) -> Unit,
    onQuickAddSpecies: (Species) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Save to Case", style = MaterialTheme.typography.titleLarge)
            Text(
                "Attach this assessment to a patient's medical record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PatientPicker(
                patients = state.patients,
                selectedPatientId = state.selectedPatientId,
                onSelect = onSelectPatient,
            )
            HorizontalDivider()
            Text("Add new patient", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = state.quickAddName,
                onValueChange = onQuickAddName,
                label = { Text("Patient name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SpeciesField(
                selected = state.quickAddSpecies,
                onSelect = onQuickAddSpecies,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onSave,
                enabled = !state.saving &&
                    (state.selectedPatientId != null || state.quickAddName.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Saving…")
                } else {
                    Text("Save to Case")
                }
            }
            Spacer(modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun PatientPicker(
    patients: List<Patient>,
    selectedPatientId: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Existing patient", style = MaterialTheme.typography.titleSmall)
        if (patients.isEmpty()) {
            Text(
                "No patients registered yet — add one below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(patients, key = { it.id }) { patient ->
                    PatientRow(
                        patient = patient,
                        selected = patient.id == selectedPatientId,
                        onSelect = { onSelect(patient.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PatientRow(
    patient: Patient,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(patient.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = patient.breed?.let { "${speciesLabel(patient.species)} · $it" }
                    ?: speciesLabel(patient.species),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
