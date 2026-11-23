package com.prudent66.vetguide.ui.screens.diagnose
import com.prudent66.vetguide.ui.components.LocalBottomBarClearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.ageLabel
import com.prudent66.vetguide.ui.components.SpeciesField
import com.prudent66.vetguide.ui.screens.cases.SaveToCaseSheet
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel
import com.prudent66.vetguide.ui.viewmodels.DiagnosticUiState
import com.prudent66.vetguide.ui.viewmodels.DiagnosticViewModel
import com.prudent66.vetguide.ui.viewmodels.ImageAnalysisUiState
import com.prudent66.vetguide.ui.viewmodels.ImageAnalysisViewModel
import com.prudent66.vetguide.ui.viewmodels.SaveToCaseUiState
import com.prudent66.vetguide.ui.viewmodels.SaveToCaseViewModel

/**
 * Guided diagnostic questionnaire flow (Phase 3) + image-analysis flow
 * (Phase 6 §4.2). The questionnaire state machine lives in
 * [DiagnosticViewModel]; the image flow in [ImageAnalysisViewModel] (kept
 * separate for function-count hygiene). When the image flow is active its
 * phases take over the screen; Cancel returns to the questionnaire Start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnoseScreen(
    onOpenCondition: (String) -> Unit,
    onCaseSaved: (String) -> Unit,
    viewModel: DiagnosticViewModel = hiltViewModel(),
    imageViewModel: ImageAnalysisViewModel = hiltViewModel(),
    saveToCaseViewModel: SaveToCaseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val imageState by imageViewModel.uiState.collectAsState()
    val saveState by saveToCaseViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        saveToCaseViewModel.savedCase.collect { caseId -> onCaseSaved(caseId) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title(state, imageState)) }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (imageState !is ImageAnalysisUiState.Idle) {
                ImageFlowPhase(
                    imageState = imageState,
                    imageViewModel = imageViewModel,
                    saveToCaseViewModel = saveToCaseViewModel,
                )
            } else {
                QuestionnairePhase(
                    state = state,
                    viewModel = viewModel,
                    imageViewModel = imageViewModel,
                    saveToCaseViewModel = saveToCaseViewModel,
                    onOpenCondition = onOpenCondition,
                )
            }
        }
    }

    (saveState as? SaveToCaseUiState.Visible)?.let { visible ->
        SaveToCaseSheet(
            state = visible,
            viewModel = saveToCaseViewModel,
            onDismiss = { saveToCaseViewModel.cancel() },
        )
    }
}

@Composable
private fun QuestionnairePhase(
    state: DiagnosticUiState,
    viewModel: DiagnosticViewModel,
    imageViewModel: ImageAnalysisViewModel,
    saveToCaseViewModel: SaveToCaseViewModel,
    onOpenCondition: (String) -> Unit,
) {
    when (val s = state) {
        is DiagnosticUiState.Start -> StartPhase(
            start = s,
            viewModel = viewModel,
            onAnalyzeImage = { ageLabel, weight -> imageViewModel.start(s.species, ageLabel, s.patientId, weight) },
        )
        is DiagnosticUiState.Wizard -> WizardPhase(wizard = s, viewModel = viewModel)
        is DiagnosticUiState.Results -> ResultsPhase(
            results = s,
            onOpenCondition = onOpenCondition,
            onRestart = viewModel::reset,
            onSaveToCase = { saveToCaseViewModel.openForQuestionnaire(s) },
        )
        is DiagnosticUiState.FlowError -> ErrorPhase(error = s, viewModel = viewModel)
    }
}

@Composable
private fun ImageFlowPhase(
    imageState: ImageAnalysisUiState,
    imageViewModel: ImageAnalysisViewModel,
    saveToCaseViewModel: SaveToCaseViewModel,
) {
    when (val s = imageState) {
        is ImageAnalysisUiState.Idle -> {}
        is ImageAnalysisUiState.Capture -> ImageCaptureScreen(
            onImageCaptured = imageViewModel::onImageCaptured,
            onCancel = imageViewModel::reset,
        )
        is ImageAnalysisUiState.Review -> ImageReviewScreen(
            imageBase64 = s.imageBase64,
            onSend = { squareCrop, context ->
                imageViewModel.sendForAnalysis(s.imageBase64, squareCrop, context)
            },
            onRetake = imageViewModel::retakeImage,
            onCancel = imageViewModel::reset,
        )
        is ImageAnalysisUiState.Analyzing -> ImageAnalyzingPhase(analysis = s)
        is ImageAnalysisUiState.Result -> ImageResultPhase(
            result = s,
            onRestart = imageViewModel::reset,
            onSaveToCase = { saveToCaseViewModel.openForImage(s) },
            onQueueForRetry = { imageViewModel.queueForRetry(s) },
        )
        is ImageAnalysisUiState.ImageError -> ImageErrorPhase(
            error = s,
            onRetake = imageViewModel::retryFromError,
            onCancel = imageViewModel::reset,
        )
        is ImageAnalysisUiState.QuotaExceeded -> QuotaExceededPhase(
            detail = s.detail,
            onRetry = { imageViewModel.start(s.species) },
            onOfflineFallback = { imageViewModel.reset() },
        )
    }
}

private fun title(state: DiagnosticUiState, imageState: ImageAnalysisUiState): String =
    if (imageState !is ImageAnalysisUiState.Idle) {
        when (imageState) {
            is ImageAnalysisUiState.Capture -> "Photo analysis"
            is ImageAnalysisUiState.Review -> "Confirm image"
            is ImageAnalysisUiState.Analyzing -> "Analyzing"
            is ImageAnalysisUiState.Result -> "Analysis Finding:"
            is ImageAnalysisUiState.ImageError -> "Photo analysis"
            is ImageAnalysisUiState.QuotaExceeded -> "Cloud analysis busy"
            ImageAnalysisUiState.Idle -> "Diagnose"
        }
    } else {
        when (state) {
            is DiagnosticUiState.Start -> "Diagnose"
            is DiagnosticUiState.Wizard -> "Guided assessment"
            is DiagnosticUiState.Results -> "Differential list"
            is DiagnosticUiState.FlowError -> "Diagnose"
        }
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
            .padding(16.dp)
            .padding(bottom = LocalBottomBarClearance.current),
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
    viewModel: DiagnosticViewModel,
    onAnalyzeImage: (String?, Double?) -> Unit,
) {
    val patients by viewModel.patients.collectAsState()
    val selectedPatient = patients.firstOrNull { it.id == start.patientId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = LocalBottomBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Clinical decision support — requires veterinary review.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        SpeciesSelection(start, viewModel)
        PatientSelection(start, patients, viewModel)
        OutlinedButton(
            onClick = { onAnalyzeImage(selectedPatient?.ageLabel(), selectedPatient?.weightKg) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Analyze a photo") }
        Button(
            onClick = viewModel::startAssessment,
            enabled = start.species != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Begin assessment") }
        start.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = "Select a species or registered patient to begin a structured " +
                "assessment or photo analysis.",
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

/**
 * Shown when all AI provider keys are exhausted (HTTP 429). Gives the vet
 * a clear message and an explicit action to fall back to the offline
 * questionnaire engine.
 */
@Composable
private fun QuotaExceededPhase(
    detail: String?,
    onRetry: () -> Unit,
    onOfflineFallback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = LocalBottomBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Cloud analysis is currently at maximum capacity. " +
                "Please try again later or use the offline questionnaire.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "The offline engine uses your selected signs against the built-in " +
                "knowledge base — no network required.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOfflineFallback, modifier = Modifier.fillMaxWidth()) {
            Text("Use Offline Questionnaire")
        }
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Retry Analysis")
        }
        if (!detail.isNullOrBlank()) {
            Text(
                "Technical detail: $detail",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

