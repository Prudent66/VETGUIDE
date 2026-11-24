package com.prudent66.vetguide.ui.screens.cases
import com.prudent66.vetguide.ui.components.LocalBottomBarClearance

import android.content.Intent
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.data.report.FarmerHandoutExporter
import com.prudent66.vetguide.domain.ai.buildPrescriptionPlan
import com.prudent66.vetguide.domain.formulary.matchDrugFromText
import com.prudent66.vetguide.domain.ai.sanitizeAiItem
import com.prudent66.vetguide.domain.model.AiArtifactPayload
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.SoapPayload
import com.prudent66.vetguide.domain.model.ageLabel
import com.prudent66.vetguide.domain.model.clinicalAnalysisText
import com.prudent66.vetguide.ui.components.PrescriptionCard
import com.prudent66.vetguide.ui.components.VoiceDictationButton
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel
import com.prudent66.vetguide.ui.viewmodels.CaseDetailUiState
import com.prudent66.vetguide.worker.ReminderScheduler
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
            .padding(16.dp)
            .padding(bottom = LocalBottomBarClearance.current),
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
        state.ai?.let { ai -> AiAnalysisCard(ai, state.patient) }
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
        CaseActionButtons(state)
    }
}

/** Owner care-sheet sharing and a one-tap follow-up reminder. */
@Composable
private fun CaseActionButtons(state: CaseDetailUiState.Success) {
    val context = LocalContext.current
    val scheduler = remember { ReminderScheduler() }
    val plan = buildPrescriptionPlan(state.ai?.analysisData, state.ai?.narrative)
    val rxLine = plan.prescriptions.firstOrNull().orEmpty()
    val drug = matchDrugFromText(rxLine)
    Button(
        onClick = {
            val handout = FarmerHandoutExporter.buildHandout(
                com.prudent66.vetguide.data.report.HandoutInput(
                    patientName = state.patient?.name ?: "Patient",
                    drugName = drug?.name ?: "Prescribed treatment",
                    doseInstructions = rxLine.ifBlank { "Follow your veterinarian's directions." },
                    isolationSteps = "Keep the animal separated from healthy animals and clean equipment daily.",
                    fromEpochMs = System.currentTimeMillis(),
                    withdrawal = com.prudent66.vetguide.data.report.HandoutWithdrawal(
                        meatDays = drug?.meatWithdrawalDays ?: 0,
                        milkDays = drug?.milkWithdrawalDays ?: 0,
                    ),
                ),
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, handout)
            }
            context.startActivity(Intent.createChooser(intent, "Share Owner Care Sheet"))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Share Owner Care Sheet") }
    OutlinedButton(
        onClick = {
            scheduler.schedule(
                context,
                "VETGUIDE follow-up",
                "Clinical re-check is due for ${state.patient?.name ?: "this animal"}.",
                24L * 60L * 60L * 1000L,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Schedule re-check reminder") }
}

@Composable
private fun AiAnalysisCard(ai: AiArtifactPayload, patient: Patient?) {
    val plan = remember(ai) { buildPrescriptionPlan(ai.analysisData, ai.narrative) }
    ArtifactCard("Analysis Finding:") {
        Text(clinicalAnalysisText(ai.narrative), style = MaterialTheme.typography.bodyMedium)
        if (plan.supportiveCare.isNotEmpty()) {
            Text(
                "Supportive field care suggested:",
                style = MaterialTheme.typography.titleSmall,
            )
            plan.supportiveCare.forEachIndexed { index, step ->
                Text(
                    "${index + 1}. ${sanitizeAiItem(step)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    PrescriptionCard(
        plan.prescriptions,
        species = patient?.species,
        weightKg = patient?.weightKg,
        modifier = Modifier.fillMaxWidth(),
    )
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
            placeholder = SUBJECTIVE_HINT,
            onDictate = { spoken -> subjective = appendDictated(subjective, spoken) },
        )
        SoapField(
            value = objective,
            onValueChange = { objective = it },
            label = "Objective (findings)",
            onDictate = { spoken -> objective = appendDictated(objective, spoken) },
        )
        SoapField(
            value = assessment,
            onValueChange = { assessment = it },
            label = "Assessment (differentials & final diagnosis)",
            onDictate = { spoken -> assessment = appendDictated(assessment, spoken) },
        )
        SoapField(
            value = plan,
            onValueChange = { plan = it },
            label = "Plan (treatment & follow-up)",
            placeholder = PLAN_HINT,
            onDictate = { spoken -> plan = appendDictated(plan, spoken) },
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
    onDictate: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        if (onDictate != null) {
            VoiceDictationButton(
                onResult = { spoken -> onDictate(appendDictated(value, spoken)) },
            )
        }
    }
}

/** Appends dictated text as a new sentence without dropping what is already there. */
private fun appendDictated(existing: String, spoken: String): String {
    val addition = spoken.trim()
    return if (addition.isEmpty()) existing else "$existing ${addition}".trim()
}

private const val SUBJECTIVE_HINT =
    "e.g., Owner reports sudden drop in egg production, swollen faces in 3 birds, " +
        "and foul-smelling nasal discharge."

private const val PLAN_HINT =
    "e.g., Administer Tylosin 50mg/kg in drinking water for 3 days. " +
        "Isolate affected birds. Follow up in 1 week."

private fun statusLabelOf(state: CaseDetailUiState.Success): String =
    state.case.status.name.lowercase().replaceFirstChar { it.uppercase() }
