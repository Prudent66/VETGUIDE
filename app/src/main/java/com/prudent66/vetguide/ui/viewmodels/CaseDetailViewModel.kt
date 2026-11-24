package com.prudent66.vetguide.ui.viewmodels

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.data.report.PdfFileWriter
import com.prudent66.vetguide.data.report.SoapPdfDocument
import com.prudent66.vetguide.data.report.SoapReportData
import com.prudent66.vetguide.domain.ai.buildPrescriptionPlan
import com.prudent66.vetguide.domain.model.AiArtifactPayload
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.CaseArtifactType
import com.prudent66.vetguide.domain.model.CasePayloadCodec
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.model.ClinicProfile
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.QuestionnaireArtifactPayload
import com.prudent66.vetguide.domain.model.SoapPayload
import com.prudent66.vetguide.domain.model.ageLabel
import com.prudent66.vetguide.domain.model.clinicalAnalysisText
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.ClinicProfileSettings
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.domain.repository.SettingsRepository
import com.prudent66.vetguide.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loaded case record with typed artifact payloads for the SOAP editor. */
sealed interface CaseDetailUiState {
    data object Loading : CaseDetailUiState

    data class Success(
        val case: Case,
        val patient: Patient?,
        val imageBase64: String?,
        val questionnaire: QuestionnaireArtifactPayload?,
        val ai: AiArtifactPayload?,
        val soap: SoapPayload,
        val soapArtifactId: String?,
        val soapCreatedAtEpochMs: Long?,
    ) : CaseDetailUiState

    data class Error(val message: String) : CaseDetailUiState
}

/** PDF export progress surfaced to the screen (Toast + button disabled). */
sealed interface PdfState {
    data object Idle : PdfState
    data object Working : PdfState
    data class Done(val fileName: String) : PdfState
    data class Failed(val message: String) : PdfState
}

/**
 * Phase 7 case detail: structured SOAP editor over the persisted artifacts.
 * Subjective/Objective/Assessment arrive pre-filled from the saved assessment
 * (questionnaire answers / clinical context / AI findings), all editable and
 * persisted back into the SOAP artifact; PDF export writes to Downloads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val caseRepository: CaseRepository,
    patientRepository: PatientRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val caseId: String = checkNotNull(savedStateHandle[Destinations.ARG_CASE_ID])

    val uiState: StateFlow<CaseDetailUiState> = caseRepository.observeCase(caseId)
        .flatMapLatest { case ->
            if (case == null) {
                flowOf(CaseDetailUiState.Error("Case not found"))
            } else {
                caseRepository.observeArtifactsForCase(caseId)
                    .combine(patientRepository.observePatient(case.patientId)) { artifacts, patient ->
                        deriveState(case, patient, artifacts)
                    }
            }
        }
        .catch { e ->
            emit(CaseDetailUiState.Error(e.message ?: "Failed to load the case"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CaseDetailUiState.Loading,
        )

    /** One-shot: the SOAP note was persisted (screen shows a confirmation). */
    val soapSaved = MutableSharedFlow<Unit>()

    /** One-shot: SOAP persistence failed (screen shows the message). */
    val saveError = MutableSharedFlow<String>()

    /** One-shot: case title/status were persisted (confirmation snackbar). */
    val caseUpdated = MutableSharedFlow<Unit>()

    /** One-shot: the case was deleted and the screen can navigate back. */
    val caseDeleted = MutableSharedFlow<Unit>()

    /** One-shot: a case action failed (snackbar). */
    val actionError = MutableSharedFlow<String>()

    private val pdfMut = MutableStateFlow<PdfState>(PdfState.Idle)
    val pdfState: StateFlow<PdfState> = pdfMut.asStateFlow()

    fun saveSoap(subjective: String, objective: String, assessment: String, plan: String) {
        val success = uiState.value as? CaseDetailUiState.Success ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val existingId = success.soapArtifactId
                caseRepository.upsertArtifact(
                    CaseArtifact(
                        id = existingId ?: UUID.randomUUID().toString(),
                        caseId = success.case.id,
                        type = CaseArtifactType.SOAP,
                        content = CasePayloadCodec.encode(
                            SoapPayload(
                                subjective = subjective,
                                objective = objective,
                                assessment = assessment,
                                plan = plan,
                            ),
                        ),
                        createdAtEpochMs = success.soapCreatedAtEpochMs ?: now,
                        updatedAtEpochMs = now,
                    ),
                )
                caseRepository.upsertCase(success.case.copy(updatedAtEpochMs = now))
                soapSaved.emit(Unit)
            } catch (e: SQLiteException) {
                saveError.emit(e.message ?: "Database error while saving the SOAP note")
            } catch (e: IllegalStateException) {
                saveError.emit(e.message ?: "Database error while saving the SOAP note")
            }
        }
    }

    /** Updates the case summary and workflow status (SOAP is saved separately). */
    fun updateCaseDetails(presentingComplaint: String, status: CaseStatus) {
        val title = presentingComplaint.trim()
        val success = uiState.value as? CaseDetailUiState.Success ?: return
        if (title.isEmpty()) return
        viewModelScope.launch {
            try {
                caseRepository.upsertCase(
                    success.case.copy(
                        presentingComplaint = title,
                        status = status,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                caseUpdated.emit(Unit)
            } catch (e: SQLiteException) {
                actionError.emit(e.message ?: "Database error while updating the case")
            } catch (e: IllegalStateException) {
                actionError.emit(e.message ?: "Database error while updating the case")
            }
        }
    }

    /** Deletes the case; Room's FK cascade also removes its artifacts. */
    fun deleteCase() {
        viewModelScope.launch {
            try {
                caseRepository.deleteCase(caseId)
                caseDeleted.emit(Unit)
            } catch (e: SQLiteException) {
                actionError.emit(e.message ?: "Database error while deleting the case")
            } catch (e: IllegalStateException) {
                actionError.emit(e.message ?: "Database error while deleting the case")
            }
        }
    }

    fun exportPdf() {
        val success = uiState.value as? CaseDetailUiState.Success ?: return
        if (pdfMut.value == PdfState.Working) return
        pdfMut.value = PdfState.Working
        viewModelScope.launch {
            try {
                val fileName = withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val name = PdfFileWriter.buildFileName(success.patient?.name, now)
                    val profile = ClinicProfileSettings.load(settingsRepository)
                    val document = SoapPdfDocument.build(soapReportData(success, now, profile))
                    try {
                        PdfFileWriter.writeToDownloads(appContext, document, name)
                    } finally {
                        document.close()
                    }
                    name
                }
                pdfMut.value = PdfState.Done(fileName)
            } catch (e: IOException) {
                pdfMut.value = PdfState.Failed(e.message ?: "Could not write the PDF file")
            } catch (e: SecurityException) {
                pdfMut.value = PdfState.Failed(
                    e.message ?: "Storage permission is needed to save the PDF",
                )
            } catch (e: IllegalStateException) {
                pdfMut.value = PdfState.Failed(e.message ?: "Could not write the PDF file")
            }
        }
    }

    private fun soapReportData(
        success: CaseDetailUiState.Success,
        nowMs: Long,
        profile: ClinicProfile,
    ): SoapReportData {
        val patient = success.patient
        val generatedAt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(nowMs))
        val ageLine = patient?.ageLabel()?.let { " · $it" }.orEmpty()
        val analysis = success.ai?.analysisData
        val plan = buildPrescriptionPlan(analysis, success.ai?.narrative)
        return SoapReportData(
            patientLine = listOfNotNull(
                patient?.name ?: "Unregistered patient",
                patient?.species?.name?.lowercase()
                    ?.replaceFirstChar { it.uppercase() },
            ).joinToString(" · ") + ageLine,
            ownerLine = patient?.ownerName,
            caseId = success.case.id,
            complaint = success.case.presentingComplaint,
            status = success.case.status.name.lowercase().replaceFirstChar { it.uppercase() },
            generatedAt = generatedAt,
            soap = success.soap,
            aiNarrative = success.ai?.narrative?.let(::clinicalAnalysisText),
            imageBase64 = success.imageBase64,
            clinicName = profile.clinicName,
            vetName = profile.vetName,
            clinicContact = profile.contact,
            animalCategory = (patient?.animalCategory?.label ?: "Domestic"),
            differentials = analysis?.suspectedConditions?.map { it.name to it.confidence }.orEmpty(),
            prescriptions = plan.prescriptions,
            supportiveCare = plan.supportiveCare,
        )
    }

    private fun deriveState(
        case: Case,
        patient: Patient?,
        artifacts: List<CaseArtifact>,
    ): CaseDetailUiState {
        val image = artifacts.lastOrNull { it.type == CaseArtifactType.IMAGE }
        val ai = artifacts.lastOrNull { it.type == CaseArtifactType.AI }
        val questionnaire = artifacts.lastOrNull { it.type == CaseArtifactType.QUESTIONNAIRE }
        val soap = artifacts.lastOrNull { it.type == CaseArtifactType.SOAP }
        return CaseDetailUiState.Success(
            case = case,
            patient = patient,
            imageBase64 = image?.content,
            questionnaire = CasePayloadCodec.decodeOrNull<QuestionnaireArtifactPayload>(
                questionnaire?.content,
            ),
            ai = CasePayloadCodec.decodeOrNull<AiArtifactPayload>(ai?.content),
            soap = CasePayloadCodec.decodeOrNull<SoapPayload>(soap?.content) ?: SoapPayload(),
            soapArtifactId = soap?.id,
            soapCreatedAtEpochMs = soap?.createdAtEpochMs,
        )
    }
}
