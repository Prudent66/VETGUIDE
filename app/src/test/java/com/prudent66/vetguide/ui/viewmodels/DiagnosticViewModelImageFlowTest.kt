package com.prudent66.vetguide.ui.viewmodels

import com.prudent66.vetguide.domain.ai.AiAttempt
import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.ai.AiProvider
import com.prudent66.vetguide.domain.ai.AiRequest
import com.prudent66.vetguide.domain.ai.FakeAiProvider
import com.prudent66.vetguide.domain.ai.OFFLINE_PROVIDER_ID
import com.prudent66.vetguide.domain.model.DiagnosticCandidate
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.QuestionnaireQuestion
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.domain.repository.QuestionnaireRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestWatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Replaces Dispatchers.Main for ViewModel unit tests (JVM has no Android main). */
class MainDispatcherRule(
    private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeQuestionnaireRepository : QuestionnaireRepository {
    override suspend fun loadQuestions(species: Species): List<QuestionnaireQuestion> = emptyList()
    override suspend fun loadCandidates(): List<DiagnosticCandidate> = emptyList()
}

private class FakePatientRepository : PatientRepository {
    override fun observePatients(): Flow<List<Patient>> = MutableStateFlow(emptyList())
    override fun observePatient(id: String): Flow<Patient?> = MutableStateFlow(null)
    override suspend fun getPatient(id: String): Patient? = null
    override suspend fun upsertPatient(patient: Patient): String = patient.id
    override suspend fun deletePatient(id: String): Boolean = true
}

/**
 * Phase 6 (§4.2) image-flow state machine, driven against the REAL
 * orchestrator with fake providers (fault-injection style, §7 — no network).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticViewModelImageFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(orchestrator: AiOrchestrator) = DiagnosticViewModel(
        questionnaireRepository = FakeQuestionnaireRepository(),
        patientRepository = FakePatientRepository(),
        orchestrator = orchestrator,
    )

    private suspend fun DiagnosticViewModel.awaitImageResult(
        timeoutMs: Long = 5_000,
    ): DiagnosticUiState.ImageAiResult {
        withTimeout(timeoutMs) {
            while (uiState.value !is DiagnosticUiState.ImageAiResult) {
                delay(10)
            }
        }
        return uiState.value as DiagnosticUiState.ImageAiResult
    }

    @Test
    fun `image phases retain the species and cancel returns to start`() = runTest {
        val viewModel = viewModel(AiOrchestrator(providers = emptyList()))

        viewModel.selectSpecies(Species.GOAT)
        viewModel.startImageAnalysis()
        val capture = viewModel.uiState.value as DiagnosticUiState.ImageCapture
        assertEquals(Species.GOAT, capture.species)

        viewModel.onImageCaptured("base64-payload")
        val review = viewModel.uiState.value as DiagnosticUiState.ImageReview
        assertEquals("base64-payload", review.imageBase64)
        assertEquals(Species.GOAT, review.species)

        viewModel.retakeImage()
        assertTrue(viewModel.uiState.value is DiagnosticUiState.ImageCapture)

        viewModel.onImageCaptured("base64-payload")
        viewModel.cancelImageFlow()
        val start = viewModel.uiState.value as DiagnosticUiState.Start
        assertEquals(Species.GOAT, start.species)
    }

    @Test
    fun `image dispatch surfaces provider provenance`() = runTest {
        val provider = FakeAiProvider(id = "fake-cloud")
        val viewModel = viewModel(AiOrchestrator(listOf(provider)))

        viewModel.selectSpecies(Species.DOG)
        viewModel.startImageAnalysis()
        viewModel.onImageCaptured("base64-payload")
        viewModel.sendForAnalysis(imageBase64 = "base64-payload", squareCrop = false)

        val result = viewModel.awaitImageResult()
        assertEquals("fake-cloud", result.providerId)
        assertTrue(result.narrative.contains("[fake-cloud]"))
        assertFalse(result.degraded)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `exhausted ladder surfaces the rule-based offline result`() = runTest {
        val primary = FakeAiProvider(id = "primary", failuresBeforeSuccess = 99)
        val viewModel = viewModel(AiOrchestrator(listOf(primary)))

        viewModel.startImageAnalysis()
        viewModel.onImageCaptured("base64-payload")
        viewModel.sendForAnalysis(imageBase64 = "base64-payload", squareCrop = false)

        val result = viewModel.awaitImageResult()
        assertEquals(OFFLINE_PROVIDER_ID, result.providerId)
        assertTrue(result.narrative.contains("requires veterinary review"))
        assertFalse(result.degraded)
    }

    @Test
    fun `cloud-AI toggle off goes straight to the rule-based result`() = runTest {
        val provider = FakeAiProvider(id = "should-not-run")
        val viewModel = viewModel(
            AiOrchestrator(
                providers = listOf(provider),
                cloudAiEnabled = { false },
            ),
        )

        viewModel.startImageAnalysis()
        viewModel.onImageCaptured("base64-payload")
        viewModel.sendForAnalysis(imageBase64 = "base64-payload", squareCrop = false)

        val result = viewModel.awaitImageResult()
        assertEquals(OFFLINE_PROVIDER_ID, result.providerId)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `questionnaire flow is untouched by image flow additions`() = runTest {
        val viewModel = viewModel(
            AiOrchestrator(
                providers = listOf(object : AiProvider {
                    override val id = "unused"

                    override suspend fun analyze(request: AiRequest): AiAttempt =
                        AiAttempt.Success("never called")
                }),
            ),
        )

        viewModel.startAssessment()
        withTimeout(5_000) {
            while ((viewModel.uiState.value as? DiagnosticUiState.Start)?.error == null) {
                delay(10)
            }
        }
        // Empty fake questionnaire: Start stays with a visible error, never hangs.
        val start = viewModel.uiState.value as DiagnosticUiState.Start
        assertTrue(start.error?.contains("No placeholder questionnaire") == true)
        assertEquals(Species.DOG, start.species)
    }
}
