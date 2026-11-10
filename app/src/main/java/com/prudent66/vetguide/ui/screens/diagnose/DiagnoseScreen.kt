package com.prudent66.vetguide.ui.screens.diagnose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.components.SpeciesField
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel
import com.prudent66.vetguide.ui.viewmodels.DiagnosticUiState
import com.prudent66.vetguide.ui.viewmodels.DiagnosticViewModel

/**
 * Guided diagnostic questionnaire flow (Phase 3): Start → Wizard → Results.
 * Placeholder scoring only — every phase shows the veterinary-review notice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnoseScreen(
    onOpenCondition: (String) -> Unit,
    viewModel: DiagnosticViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val patients by viewModel.patients.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(flowTitle(state)) }) }) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                is DiagnosticUiState.Start -> StartPhase(
                    start = s,
                    patients = patients,
                    viewModel = viewModel,
                )
                is DiagnosticUiState.Wizard -> WizardPhase(wizard = s, viewModel = viewModel)
                is DiagnosticUiState.Results -> ResultsPhase(
                    results = s,
                    onOpenCondition = onOpenCondition,
                    onRestart = viewModel::reset,
                )
                is DiagnosticUiState.FlowError -> ErrorPhase(error = s, viewModel = viewModel)
                is DiagnosticUiState.ImageCapture -> ImageCaptureScreen(
                    onImageCaptured = viewModel::onImageCaptured,
                    onCancel = viewModel::cancelImageFlow,
                )
                is DiagnosticUiState.ImageReview -> ImageReviewScreen(
                    imageBase64 = s.imageBase64,
                    onSend = { squareCrop ->
                        viewModel.sendForAnalysis(s.imageBase64, squareCrop)
                    },
                    onRetake = viewModel::retakeImage,
                    onCancel = viewModel::cancelImageFlow,
                )
                is DiagnosticUiState.ImageAnalyzing -> ImageAnalyzingPhase(analysis = s)
                is DiagnosticUiState.ImageAiResult -> ImageResultPhase(
                    result = s,
                    onRestart = viewModel::reset,
                )
            }
        }
    }
}

private fun flowTitle(state: DiagnosticUiState): String = when (state) {
    is DiagnosticUiState.Start -> "Diagnose"
    is DiagnosticUiState.Wizard -> "Guided assessment"
    is DiagnosticUiState.Results -> "Differential list"
    is DiagnosticUiState.FlowError -> "Diagnose"
    is DiagnosticUiState.ImageCapture -> "Photo analysis"
    is DiagnosticUiState.ImageReview -> "Confirm image"
    is DiagnosticUiState.ImageAnalyzing -> "Analyzing"
    is DiagnosticUiState.ImageAiResult -> "AI analysis"
}

/** Fallback after a hard database failure: message + Retry / Start over. */
@Composable
private fun ErrorPhase(
    error: DiagnosticUiState.FlowError,
    viewModel: DiagnosticViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Something went wrong",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(error.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = viewModel::retryAssessment,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Retry") }
        OutlinedButton(
            onClick = viewModel::reset,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start over") }
    }
}

@Composable
private fun StartPhase(
    start: DiagnosticUiState.Start,
    patients: List<Patient>,
    viewModel: DiagnosticViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Placeholder diagnostic engine — requires veterinary review.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        SpeciesSelection(start, viewModel)
        PatientSelection(start, patients, viewModel)
        Button(
            onClick = viewModel::startAssessment,
            enabled = start.species != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Begin assessment") }
        OutlinedButton(
            onClick = viewModel::startImageAnalysis,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Analyze a photo (Cloud AI)") }
        start.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = "Placeholder flows are seeded for dogs and poultry; other species " +
                "arrive with content review.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeciesSelection(
    start: DiagnosticUiState.Start,
    viewModel: DiagnosticViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Species", style = MaterialTheme.typography.labelLarge)
        SpeciesField(
            selected = start.species ?: Species.DOG,
            onSelect = viewModel::selectSpecies,
            modifier = Modifier.fillMaxWidth(),
        )
        if (start.patientId != null && start.species != null) {
            Text(
                text = "Species follows the selected patient (${speciesLabel(start.species)}). " +
                    "Picking a species clears the patient link.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PatientSelection(
    start: DiagnosticUiState.Start,
    patients: List<Patient>,
    viewModel: DiagnosticViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPatient = patients.firstOrNull { it.id == start.patientId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Patient (optional)", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = selectedPatient?.name?.let { "Patient: $it" }
                    ?: "Not recorded — direct species assessment",
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Not recorded") }, onClick = {
                viewModel.selectPatient(null)
                expanded = false
            })
            patients.forEach { patient ->
                DropdownMenuItem(
                    text = { Text("${patient.name} (${speciesLabel(patient.species)})") },
                    onClick = {
                        viewModel.selectPatient(patient)
                        expanded = false
                    },
                )
            }
        }
    }
}

