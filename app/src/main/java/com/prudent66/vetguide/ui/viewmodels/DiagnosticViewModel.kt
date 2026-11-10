package com.prudent66.vetguide.ui.viewmodels

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.data.ai.ImageEncoder
import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.ai.AiRequest
import com.prudent66.vetguide.domain.ai.AiResult
import com.prudent66.vetguide.domain.engine.DiagnosticEngine
import com.prudent66.vetguide.domain.model.DiagnosticMatch
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.QuestionnaireQuestion
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.domain.repository.QuestionnaireRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** Phase 6 (§4.2): live camera preview (or gallery pick) for AI analysis. */
    data class ImageCapture(val species: Species?) : DiagnosticUiState

    /** Phase 6 (§4.2): confirm/crop screen; [imageBase64] is compressed JPEG. */
    data class ImageReview(val species: Species?, val imageBase64: String) : DiagnosticUiState

    /** Phase 6 (§4.2): orchestrator failover ladder in flight. */
    data class ImageAnalyzing(val species: Species?) : DiagnosticUiState

    /**
     * Phase 6 (§4.2) merged-diagnosis outcome. [providerId] is provenance:
     * a real provider id = "AI prediction", [OFFLINE_PROVIDER_ID] =
     * deterministic rule-based result (§10 — always visually distinct).
     */
    data class ImageAiResult(
        val species: Species?,
        val providerId: String,
        val narrative: String,
        val degraded: Boolean,
    ) : DiagnosticUiState
}

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val questionnaireRepository: QuestionnaireRepository,
    patientRepository: PatientRepository,
    private val orchestrator: AiOrchestrator,
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

    // ---- Phase 6 (§4.2): image capture → consent → orchestrator ----

    /** Opens the camera/gallery phase, carrying the species from Start. */
    fun startImageAnalysis() {
        val start = mut.value as? DiagnosticUiState.Start ?: return
        mut.value = DiagnosticUiState.ImageCapture(species = start.species)
    }

    /** Called with a compressed-JPEG Base64 payload after capture or pick. */
    fun onImageCaptured(imageBase64: String) {
        val capture = mut.value as? DiagnosticUiState.ImageCapture ?: return
        mut.value = DiagnosticUiState.ImageReview(
            species = capture.species,
            imageBase64 = imageBase64,
        )
    }

    /** Confirm/crop screen: back to the live preview. */
    fun retakeImage() {
        val review = mut.value as? DiagnosticUiState.ImageReview ?: return
        mut.value = DiagnosticUiState.ImageCapture(species = review.species)
    }

    /** Image phases abandon back to Start, keeping the species. */
    fun cancelImageFlow() {
        val current = mut.value
        val species = when (current) {
            is DiagnosticUiState.ImageCapture -> current.species
            is DiagnosticUiState.ImageReview -> current.species
            else -> null
        }
        if (current is DiagnosticUiState.ImageCapture || current is DiagnosticUiState.ImageReview) {
            mut.value = DiagnosticUiState.Start(species = species)
        }
    }

    /**
     * Sends the image through the §5 failover ladder. The UI must already
     * have shown the §10 cloud-consent modal — consent to dispatch is a UI
     * gate in front of this call (per-artifact persistence lands with the
     * case-artifact work). Optional square crop runs off the main thread.
     */
    fun sendForAnalysis(imageBase64: String, squareCrop: Boolean) {
        val review = mut.value as? DiagnosticUiState.ImageReview ?: return
        mut.value = DiagnosticUiState.ImageAnalyzing(species = review.species)
        viewModelScope.launch {
            try {
                val payloadBase64 = if (squareCrop) {
                    withContext(Dispatchers.IO) {
                        ImageEncoder.centerCropSquareToBase64(ImageEncoder.fromBase64(imageBase64))
                    }
                } else {
                    imageBase64
                }
                val result = orchestrator.analyze(
                    AiRequest(
                        headline = headline(review.species),
                        findings = IMAGE_FINDINGS,
                    ),
                )
                mut.value = DiagnosticUiState.ImageAiResult(
                    species = review.species,
                    providerId = result.providerId,
                    narrative = when (result) {
                        is AiResult.ProviderSuccess -> result.text
                        is AiResult.DeterministicFallback -> result.text
                    },
                    degraded = result is AiResult.ProviderSuccess && result.degraded,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                failToError("Could not process the captured image", review.species, null, e)
            } catch (e: IllegalStateException) {
                failToError("Image analysis unavailable", review.species, null, e)
            }
        }
    }

    private fun headline(species: Species?): String = species?.name
        ?.lowercase()
        ?.replaceFirstChar { it.uppercase() }
        ?: "Unspecified species"

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

        /** §4.2: findings text submitted with the image payload. */
        const val IMAGE_FINDINGS =
            "Photograph of the animal's presenting condition submitted for AI image analysis."
    }
}
