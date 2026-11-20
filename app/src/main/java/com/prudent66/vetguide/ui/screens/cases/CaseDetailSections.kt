package com.prudent66.vetguide.ui.screens.cases

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.ai.sanitizeAiItem
import com.prudent66.vetguide.domain.model.AiArtifactPayload
import com.prudent66.vetguide.domain.model.SoapPayload
import com.prudent66.vetguide.domain.model.ageLabel
import com.prudent66.vetguide.domain.model.clinicalAnalysisText
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel
import com.prudent66.vetguide.ui.viewmodels.CaseDetailUiState
import com.prudent66.vetguide.ui.viewmodels.CaseDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Body of the case record: patient info, artifacts, editable SOAP, export. */
@Composable
internal fun CaseDetailContent(
    state: CaseDetailUiState.Success,
    pdfWorking: Boolean,
    viewModel: CaseDetailViewModel,
    onExportPdf: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PatientInfoCard(state)
        state.questionnaire?.let { questionnaire ->
            ArtifactCard("Questionnaire answers") {
                questionnaire.answers.forEach { answer ->
                    Text(
                        "${answer.question}: ${answer.answers.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        state.imageBase64?.let { base64 ->
            Base64Image(base64 = base64, contentDescription = "Saved case image")
        }
        state.ai?.let { ai -> AiAnalysisCard(ai) }
        HorizontalDivider()
        SoapEditor(soap = state.soap, onSave = viewModel::saveSoap)
        Button(
            onClick = onExportPdf,
            enabled = !pdfWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (pdfWorking) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Building PDF…")
            } else {
                Text("Export PDF")
            }
        }
    }
}

@Composable
private fun AiAnalysisCard(ai: AiArtifactPayload) {
    ArtifactCard("Analysis Finding:") {
        Text(clinicalAnalysisText(ai.narrative), style = MaterialTheme.typography.bodyMedium)
        val steps = ai.analysisData?.recommendedNextSteps.orEmpty()
        if (steps.isNotEmpty()) {
            Text(
                "Practical field treatments suggested:",
                style = MaterialTheme.typography.titleSmall,
            )
            steps.forEachIndexed { index, step ->
                Text(
                    "${index + 1}. ${sanitizeAiItem(step)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PatientInfoCard(state: CaseDetailUiState.Success) {
    val patient = state.patient
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = patient?.name ?: "Unregistered patient",
                style = MaterialTheme.typography.titleLarge,
            )
            if (patient != null) {
                Text(
                    text = listOfNotNull(
                        speciesLabel(patient.species),
                        patient.breed,
                        patient.ageLabel(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                patient.ownerName?.let { owner ->
                    Text(
                        "Owner: $owner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${state.case.presentingComplaint} · ${statusLabelOf(state)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtifactCard(heading: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(heading, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** Saved camera/gallery image stored as Base64 in the IMAGE artifact. */
@Composable
private fun Base64Image(base64: String, contentDescription: String) {
    val preview by produceState<ImageBitmap?>(initialValue = null, base64) {
        value = withContext(Dispatchers.Default) {
            val bytes = com.prudent66.vetguide.data.ai.ImageEncoder.fromBase64(base64)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    preview?.let { bitmap ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

/** The four editable SOAP sections, pre-filled from the persisted artifact. */
@Composable
internal fun SoapEditor(
    soap: SoapPayload,
    onSave: (subjective: String, objective: String, assessment: String, plan: String) -> Unit,
) {
    var subjective by remember(soap) { mutableStateOf(soap.subjective) }
    var objective by remember(soap) { mutableStateOf(soap.objective) }
    var assessment by remember(soap) { mutableStateOf(soap.assessment) }
    var plan by remember(soap) { mutableStateOf(soap.plan) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SOAP note", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pre-filled from this assessment — tap any box to edit, then save.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SoapField(
            value = subjective,
            onValueChange = { subjective = it },
            label = "Subjective (history / anamnesis)",
            placeholder = SUBJECTIVE_PLACEHOLDER,
        )
        SoapField(
            value = objective,
            onValueChange = { objective = it },
            label = "Objective (findings)",
        )
        SoapField(
            value = assessment,
            onValueChange = { assessment = it },
            label = "Assessment (differentials & final diagnosis)",
        )
        SoapField(
            value = plan,
            onValueChange = { plan = it },
            label = "Plan (treatment & follow-up)",
            placeholder = PLAN_PLACEHOLDER,
            minLines = 2,
        )
        Button(
            onClick = {
                onSave(subjective.trim(), objective.trim(), assessment.trim(), plan.trim())
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save SOAP note")
        }
    }
}

@Composable
private fun SoapField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    minLines: Int = 3,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
    )
}

private const val SUBJECTIVE_PLACEHOLDER =
    "e.g., Owner reports sudden drop in egg production, swollen faces in 3 birds, " +
        "and foul-smelling nasal discharge."

private const val PLAN_PLACEHOLDER =
    "e.g., Administer Tylosin 50mg/kg in drinking water for 3 days. " +
        "Isolate affected birds. Follow up in 1 week."

private fun statusLabelOf(state: CaseDetailUiState.Success): String =
    state.case.status.name.lowercase().replaceFirstChar { it.uppercase() }
