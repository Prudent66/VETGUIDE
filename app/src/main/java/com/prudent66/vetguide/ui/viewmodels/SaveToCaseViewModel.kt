package com.prudent66.vetguide.ui.viewmodels

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.ai.sanitizeAiItem
import com.prudent66.vetguide.domain.model.AnsweredQuestionPayload
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.CaseArtifactType
import com.prudent66.vetguide.domain.model.CasePayloadCodec
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.QuestionnaireArtifactPayload
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.SoapPayload
import com.prudent66.vetguide.domain.model.AiArtifactPayload
import com.prudent66.vetguide.domain.model.displayName
import com.prudent66.vetguide.domain.model.renderDifferentialsSeed
import com.prudent66.vetguide.domain.model.renderPlanSeed
import com.prudent66.vetguide.domain.model.renderSubjectiveSeed
import com.prudent66.vetguide.domain.model.renderSuspectedConditionsSeed
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sheet state for attaching a finished assessment to a patient's record. */
sealed interface SaveToCaseUiState {
    data object Hidden : SaveToCaseUiState

    data class Visible(
        val patients: List<Patient>,
        val selectedPatientId: String? = null,
        val quickAddName: String = "",
        val quickAddSpecies: Species = Species.DOG,
        val saving: Boolean = false,
        val error: String? = null,
    ) : SaveToCaseUiState
}

/**
 * Phase 7: turns a transient assessment (questionnaire results or image
 * analysis) into a permanent case record: Case + IMAGE/AI/QUESTIONNAIRE
 * artifacts + a pre-filled SOAP artifact. The sheet offers existing patients
 * or a quick-add patient (name + species; details editable later).
 */
@HiltViewModel
class SaveToCaseViewModel @Inject constructor(
    private val caseRepository: CaseRepository,
    private val patientRepository: PatientRepository,
) : ViewModel() {

    private val mut = MutableStateFlow<SaveToCaseUiState>(SaveToCaseUiState.Hidden)
    val uiState: StateFlow<SaveToCaseUiState> = mut.asStateFlow()

    /** One-shot: the id of the freshly saved case (UI navigates to its detail). */
    val savedCase = MutableSharedFlow<String>()

    val patients: StateFlow<List<Patient>> = patientRepository.observePatients()
        .catch { throwable ->
            if (throwable is SQLiteException || throwable is IllegalStateException) {
                android.util.Log.e(TAG, "Patient list unavailable", throwable)
                emit(emptyList())
            } else {
                throw throwable
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    init {
        // Room emits asynchronously; keep the visible sheet in sync whenever
        // the patient list changes (e.g. a patient added from another screen).
        viewModelScope.launch {
            patients.collect { loaded ->
                (mut.value as? SaveToCaseUiState.Visible)?.let { visible ->
                    mut.value = visible.copy(patients = loaded)
                }
            }
        }
    }

    /** Assessment payload captured when the sheet opens (results are transient). */
    private data class PendingAssessment(
        val presentingComplaint: String,
        val defaultPatientId: String?,
        val subjectiveSeed: String,
        val objectiveSeed: String,
        val assessmentSeed: String,
        val planSeed: String,
        val imageBase64: String?,
        val aiContent: String?,
        val questionnaireContent: String?,
    )

    private var pending: PendingAssessment? = null

    fun openForQuestionnaire(results: DiagnosticUiState.Results) {
        pending = PendingAssessment(
            presentingComplaint = "Guided questionnaire — ${results.species.displayName()}",
            defaultPatientId = results.patientId,
            subjectiveSeed = renderSubjectiveSeed(clinicalContext = null, answers = results.answers),
            objectiveSeed = "",
            assessmentSeed = renderDifferentialsSeed(results.matches),
            planSeed = "",
            imageBase64 = null,
            aiContent = null,
            questionnaireContent = CasePayloadCodec.encode(
                QuestionnaireArtifactPayload(
                    species = results.species?.name,
                    answers = results.answers,
                ),
            ),
        )
        mut.value = SaveToCaseUiState.Visible(
            patients = patients.value,
            selectedPatientId = pending?.defaultPatientId,
            quickAddSpecies = results.species ?: Species.DOG,
        )
    }

    fun openForImage(result: ImageAnalysisUiState.Result) {
        pending = PendingAssessment(
            presentingComplaint = "Photo analysis — ${result.species.displayName()}",
            defaultPatientId = result.patientId,
            subjectiveSeed = result.clinicalContext?.trim().orEmpty(),
            objectiveSeed = result.analysisData?.findings
                ?.map { sanitizeAiItem(it) }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { finding -> "• $finding" }
                ?: result.narrative,
            assessmentSeed = renderSuspectedConditionsSeed(result.analysisData).orEmpty(),
            planSeed = renderPlanSeed(result.analysisData, result.narrative).orEmpty(),
            imageBase64 = result.imageBase64,
            aiContent = CasePayloadCodec.encode(
                AiArtifactPayload(
                    narrative = result.narrative,
                    providerId = result.providerId,
                    degraded = result.degraded,
                    analysisData = result.analysisData,
                ),
            ),
            questionnaireContent = null,
        )
        mut.value = SaveToCaseUiState.Visible(
            patients = patients.value,
            selectedPatientId = result.patientId,
            quickAddSpecies = result.species ?: Species.DOG,
        )
    }

    fun selectPatient(patientId: String?) {
        val visible = mut.value as? SaveToCaseUiState.Visible ?: return
        mut.value = visible.copy(
            selectedPatientId = patientId,
            // Choosing a registered patient discards any half-typed new name.
            quickAddName = if (patientId == null) visible.quickAddName else "",
            error = null,
        )
    }

    fun setQuickAddName(name: String) {
        val visible = mut.value as? SaveToCaseUiState.Visible ?: return
        mut.value = visible.copy(
            quickAddName = name,
            // A non-blank new name unselects the existing-patient radio.
            selectedPatientId = if (name.isBlank()) visible.selectedPatientId else null,
            error = null,
        )
    }

    fun setQuickAddSpecies(species: Species) {
        val visible = mut.value as? SaveToCaseUiState.Visible ?: return
        mut.value = visible.copy(quickAddSpecies = species)
    }

    /** Close the sheet without saving; the captured assessment is discarded. */
    fun cancel() {
        pending = null
        mut.value = SaveToCaseUiState.Hidden
    }

    fun save() {
        val visible = mut.value as? SaveToCaseUiState.Visible ?: return
        val assessment = pending ?: return
        when {
            visible.saving -> Unit
            visible.selectedPatientId == null && visible.quickAddName.isBlank() -> {
                mut.value = visible.copy(
                    error = "Select an existing patient or enter a name to add one.",
                )
            }
            else -> {
                mut.value = visible.copy(saving = true, error = null)
                viewModelScope.launch {
                    try {
                        val caseId = persist(assessment, visible)
                        pending = null
                        mut.value = SaveToCaseUiState.Hidden
                        savedCase.emit(caseId)
                    } catch (e: SQLiteException) {
                        val message = e.message ?: "Database error while saving the case"
                        android.util.Log.e(TAG, message)
                        mut.value = visible.copy(saving = false, error = message)
                    } catch (e: IllegalStateException) {
                        val message = e.message ?: "Database error while saving the case"
                        android.util.Log.e(TAG, message)
                        mut.value = visible.copy(saving = false, error = message)
                    }
                }
            }
        }
    }

    private suspend fun persist(
        assessment: PendingAssessment,
        visible: SaveToCaseUiState.Visible,
    ): String {
        val now = System.currentTimeMillis()
        val patientId = assessment.defaultPatientId
            ?: visible.selectedPatientId
            ?: quickAddPatient(visible, now)
        val caseId = UUID.randomUUID().toString()
        caseRepository.upsertCase(
            Case(
                id = caseId,
                patientId = patientId,
                presentingComplaint = assessment.presentingComplaint,
                status = CaseStatus.INVESTIGATING,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        assessment.imageBase64?.let { image ->
            upsertArtifact(caseId, CaseArtifactType.IMAGE, image, now)
        }
        assessment.aiContent?.let { content ->
            upsertArtifact(caseId, CaseArtifactType.AI, content, now)
        }
        assessment.questionnaireContent?.let { content ->
            upsertArtifact(caseId, CaseArtifactType.QUESTIONNAIRE, content, now)
        }
        upsertArtifact(
            caseId,
            CaseArtifactType.SOAP,
            CasePayloadCodec.encode(
                SoapPayload(
                    subjective = assessment.subjectiveSeed,
                    objective = assessment.objectiveSeed,
                    assessment = assessment.assessmentSeed,
                    plan = assessment.planSeed,
                ),
            ),
            now,
        )
        return caseId
    }

    private suspend fun upsertArtifact(
        caseId: String,
        type: CaseArtifactType,
        content: String,
        now: Long,
    ) {
        caseRepository.upsertArtifact(
            CaseArtifact(
                id = UUID.randomUUID().toString(),
                caseId = caseId,
                type = type,
                content = content,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    private suspend fun quickAddPatient(visible: SaveToCaseUiState.Visible, now: Long): String {
        val patient = Patient(
            id = UUID.randomUUID().toString(),
            name = visible.quickAddName.trim(),
            species = visible.quickAddSpecies,
            breed = null,
            sex = PatientSex.UNKNOWN,
            neuterStatus = NeuterStatus.UNKNOWN,
            dateOfBirthEpochMs = null,
            ageValue = null,
            ageUnit = null,
            weightKg = null,
            weightUpdatedAtEpochMs = null,
            ownerName = null,
            ownerPhone = null,
            ownerNotes = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        return patientRepository.upsertPatient(patient)
    }

    private companion object {
        const val TAG = "SaveToCaseViewModel"
    }
}
