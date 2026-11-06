package com.prudent66.vetguide.ui.viewmodels

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Guided questionnaire flow state (roadmap Phase 3):
 * Start (species/patient) → Wizard (one question per step) → Results.
 * All scores are placeholder — the UI displays the veterinary-review notice.
 */
sealed interface DiagnosticUiState {
    data object Loading : DiagnosticUiState

    data class Start(
        val species: Species? = null,
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
}

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val questionnaireRepository: QuestionnaireRepository,
    patientRepository: PatientRepository,
) : ViewModel() {

    private val mut = MutableStateFlow<DiagnosticUiState>(DiagnosticUiState.Loading)
    val uiState: StateFlow<DiagnosticUiState> = mut.asStateFlow()

    val patients: StateFlow<List<Patient>> = patientRepository.observePatients()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun selectSpecies(species: Species) {
        mutateStart { it.copy(species = species, patientId = null, error = null) }
    }

    fun selectPatient(patient: Patient) {
        mutateStart { it.copy(species = patient.species, patientId = patient.id, error = null) }
    }

    fun clearPatient() {
        mutateStart { it.copy(patientId = null) }
    }

    fun startAssessment() {
        val start = mut.value as? DiagnosticUiState.Start ?: return
        val species = start.species ?: return
        viewModelScope.launch {
            val questions = questionnaireRepository.loadQuestions(species)
            if (questions.isEmpty()) {
                mutateStart { it.copy(error = "No placeholder questionnaire for this species yet") }
            } else {
                mut.value = DiagnosticUiState.Wizard(
                    species = species,
                    questions = questions,
                    index = 0,
                    selections = emptyMap(),
                )
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

    /** @return true when the flow advanced to the results screen. */
    fun nextStep(): Boolean {
        val wizard = mut.value as? DiagnosticUiState.Wizard ?: return false
        if (wizard.index < wizard.questions.lastIndex) {
            mut.value = wizard.copy(index = wizard.index + 1)
            return false
        }
        evaluate(wizard)
        return true
    }

    fun previousStep() {
        val wizard = mut.value as? DiagnosticUiState.Wizard ?: return
        if (wizard.index > 0) {
            mut.value = wizard.copy(index = wizard.index - 1)
        } else {
            reset()
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
            val matches = DiagnosticEngine.evaluate(
                selectedSignIds = selectedSignIds,
                candidates = questionnaireRepository.loadCandidates(),
            )
            mut.value = DiagnosticUiState.Results(
                matches = matches,
                species = wizard.species,
            )
        }
    }

    private fun mutateStart(block: (DiagnosticUiState.Start) -> DiagnosticUiState.Start) {
        val start = mut.value as? DiagnosticUiState.Start ?: return
        mut.value = block(start)
    }
}
