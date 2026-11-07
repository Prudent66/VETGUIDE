package com.prudent66.vetguide.ui.viewmodels

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.engine.DiagnosticEngine
import com.prudent66.vetguide.domain.model.DiagnosticMatch
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.QuestionnaireQuestion
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.domain.repository.QuestionnaireRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Guided questionnaire flow state (roadmap Phase 3):
 * Start (species/patient) → Wizard (one question per step) → Results.
 *
 * [Start] is the initial AND fallback state (species defaults to Dog with a
 * visible error + retry via "Begin assessment"), so the screen can never hang
 * on a transitional value. [FlowError] covers hard database failures and
 * offers an explicit Retry button (re-scores the wizard when possible).
 */
sealed interface DiagnosticUiState {
    data class Start(
        val species: Species? = Species.DOG,
        val patientId: String? = null,
        val error: String? = null,
    ) : DiagnosticUiState

    data class Wizard(
        val species: Species,
        val questions: List<QuestionnaireQuestion>,
        val index: Int,
        /** questionId → selected option ids. */
        val selections: Map<String, Set<String>>,
    ) : DiagnosticUiState

    data class Results(
        val matches: List<DiagnosticMatch>,
        val species: Species?,
    ) : DiagnosticUiState

    /** Hard database failure; retryWizard != null re-runs scoring only. */
    data class FlowError(
        val message: String,
        val species: Species?,
        val retryWizard: Wizard?,
    ) : DiagnosticUiState
}

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val questionnaireRepository: QuestionnaireRepository,
    patientRepository: PatientRepository,
) : ViewModel() {

    private val mut = MutableStateFlow<DiagnosticUiState>(DiagnosticUiState.Start())
    val uiState: StateFlow<DiagnosticUiState> = mut.asStateFlow()

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
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun selectSpecies(species: Species) {
        val start = mut.value as? DiagnosticUiState.Start ?: return
        mut.value = start.copy(species = species, patientId = null, error = null)
    }

    /** Null patient clears the link; a real patient locks the species. */
    fun selectPatient(patient: Patient?) {
        val start = mut.value as? DiagnosticUiState.Start ?: return
        mut.value = start.copy(
            species = patient?.species ?: start.species,
            patientId = patient?.id,
            error = null,
        )
    }

    fun startAssessment() {
        val start = mut.value as? DiagnosticUiState.Start ?: return
        val species = start.species ?: return
        viewModelScope.launch {
            try {
                val questions = questionnaireRepository.loadQuestions(species)
                if (questions.isEmpty()) {
                    mut.value = start.copy(
                        error = "No placeholder questionnaire loaded for this species — " +
                            "try again in a moment (first-launch seeding may still be running) " +
                            "or pick another species.",
                    )
                } else {
                    mut.value = DiagnosticUiState.Wizard(
                        species = species,
                        questions = questions,
                        index = 0,
                        selections = emptyMap(),
                    )
                }
            } catch (e: SQLiteException) {
                failToError("Database unavailable while loading the questionnaire", species, null, e)
            } catch (e: IllegalStateException) {
                failToError("Database unavailable while loading the questionnaire", species, null, e)
            }
        }
    }

    fun toggleOption(optionId: String) {
        val wizard = mut.value as? DiagnosticUiState.Wizard ?: return
        val question = wizard.questions[wizard.index]
        val current = wizard.selections[question.id].orEmpty()
        val updated = if (optionId in current) current - optionId else current + optionId
        mut.value = wizard.copy(selections = wizard.selections + (question.id to updated))
    }

    fun nextStep() {
        val wizard = mut.value as? DiagnosticUiState.Wizard ?: return
        if (wizard.index < wizard.questions.lastIndex) {
            mut.value = wizard.copy(index = wizard.index + 1)
        } else {
            evaluate(wizard)
        }
    }

    fun previousStep() {
        val wizard = mut.value as? DiagnosticUiState.Wizard ?: return
        if (wizard.index > 0) {
            mut.value = wizard.copy(index = wizard.index - 1)
        } else {
            reset()
        }
    }

    /** Retry point: re-scores a retained wizard or restarts from Start. */
    fun retryAssessment() {
        val error = mut.value as? DiagnosticUiState.FlowError ?: return
        val wizard = error.retryWizard
        if (wizard != null) {
            evaluate(wizard)
        } else {
            mut.value = DiagnosticUiState.Start(species = error.species)
        }
    }

    fun reset() {
        mut.value = DiagnosticUiState.Start()
    }

    private fun evaluate(wizard: DiagnosticUiState.Wizard) {
        viewModelScope.launch {
            val selectedSignIds = wizard.questions
                .flatMap { question -> question.options.filter { it.id in wizard.selections[question.id].orEmpty() } }
                .mapNotNull { it.symptomId }
                .toSet()
            try {
                val matches = DiagnosticEngine.evaluate(
                    selectedSignIds = selectedSignIds,
                    candidates = questionnaireRepository.loadCandidates(),
                )
                mut.value = DiagnosticUiState.Results(matches = matches, species = wizard.species)
            } catch (e: SQLiteException) {
                failToError("Database unavailable while scoring the assessment", wizard.species, wizard, e)
            } catch (e: IllegalStateException) {
                failToError("Database unavailable while scoring the assessment", wizard.species, wizard, e)
            }
        }
    }

    private fun failToError(
        message: String,
        species: Species?,
        wizard: DiagnosticUiState.Wizard?,
        cause: Exception,
    ) {
        android.util.Log.e(TAG, message, cause)
        mut.value = DiagnosticUiState.FlowError(
            message = message,
            species = species,
            retryWizard = wizard,
        )
    }

    private companion object {
        const val TAG = "DiagnosticViewModel"
    }
}
