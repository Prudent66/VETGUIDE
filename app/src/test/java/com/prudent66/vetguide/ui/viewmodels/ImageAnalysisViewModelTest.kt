package com.prudent66.vetguide.ui.viewmodels

import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.ai.FakeAiProvider
import com.prudent66.vetguide.domain.ai.OFFLINE_PROVIDER_ID
import com.prudent66.vetguide.domain.model.Species
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Replaces Dispatchers.Main for ViewModel unit tests (JVM has no Android main). */
class MainDispatcherRule(
    private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(dispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}

/**
 * Phase 6 (§4.2) image-flow state machine, driven against the REAL
 * orchestrator with fake providers (fault-injection style, §7 — no network,
 * no crop path: the Android Bitmap half is verified on device).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageAnalysisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(orchestrator: AiOrchestrator) = ImageAnalysisViewModel(orchestrator)

    private suspend fun ImageAnalysisViewModel.awaitResult(
        timeoutMs: Long = 5_000,
    ): ImageAnalysisUiState.Result {
        withTimeout(timeoutMs) {
            while (uiState.value !is ImageAnalysisUiState.Result) {
                delay(10)
            }
        }
        return uiState.value as ImageAnalysisUiState.Result
    }

    @Test
    fun `capture phases retain the species and reset returns to idle`() = runTest {
        val viewModel = viewModel(AiOrchestrator(providers = emptyList()))
        assertTrue(viewModel.uiState.value is ImageAnalysisUiState.Idle)

        viewModel.start(Species.GOAT)
        val capture = viewModel.uiState.value as ImageAnalysisUiState.Capture
        assertEquals(Species.GOAT, capture.species)

        viewModel.onImageCaptured("cGF5bG9hZA==")
        val review = viewModel.uiState.value as ImageAnalysisUiState.Review
        assertEquals("cGF5bG9hZA==", review.imageBase64)
        assertEquals(Species.GOAT, review.species)

        viewModel.retakeImage()
        assertTrue(viewModel.uiState.value is ImageAnalysisUiState.Capture)

        viewModel.onImageCaptured("cGF5bG9hZA==")
        viewModel.reset()
        assertTrue(viewModel.uiState.value is ImageAnalysisUiState.Idle)
    }

    @Test
    fun `dispatch passes the image payload and surfaces provider provenance`() = runTest {
        val provider = FakeAiProvider(id = "fake-cloud")
        val viewModel = viewModel(AiOrchestrator(listOf(provider)))

        viewModel.start(Species.DOG)
        viewModel.onImageCaptured("cGF5bG9hZA==")
        viewModel.sendForAnalysis(imageBase64 = "cGF5bG9hZA==", squareCrop = false)

        val result = viewModel.awaitResult()
        assertEquals("fake-cloud", result.providerId)
        assertTrue(result.narrative.contains("[fake-cloud]"))
        assertFalse(result.degraded)
        assertEquals(1, provider.callCount)
        // FakeAiProvider is text-only: no structured sections, narrative path.
        assertTrue(result.analysisData == null)
    }

    @Test
    fun `exhausted ladder surfaces the rule-based offline result`() = runTest {
        val primary = FakeAiProvider(id = "primary", failuresBeforeSuccess = 99)
        val viewModel = viewModel(AiOrchestrator(listOf(primary)))

        viewModel.start(Species.DOG)
        viewModel.onImageCaptured("cGF5bG9hZA==")
        viewModel.sendForAnalysis(imageBase64 = "cGF5bG9hZA==", squareCrop = false)

        val result = viewModel.awaitResult()
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

        viewModel.start(Species.DOG)
        viewModel.onImageCaptured("cGF5bG9hZA==")
        viewModel.sendForAnalysis(imageBase64 = "cGF5bG9hZA==", squareCrop = false)

        val result = viewModel.awaitResult()
        assertEquals(OFFLINE_PROVIDER_ID, result.providerId)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `clinical context is trimmed and blank context is treated as absent`() = runTest {
        val provider = FakeAiProvider(id = "fake-cloud")
        val viewModel = viewModel(AiOrchestrator(listOf(provider)))

        viewModel.start(Species.DOG)
        viewModel.onImageCaptured("cGF5bG9hZA==")
        viewModel.sendForAnalysis(
            imageBase64 = "cGF5bG9hZA==",
            squareCrop = false,
            clinicalContext = "  Vomiting for 2 days  ",
        )
        viewModel.awaitResult()
        assertEquals("Vomiting for 2 days", provider.lastRequest?.clinicalContext)

        viewModel.reset()
        viewModel.start(Species.DOG)
        viewModel.onImageCaptured("cGF5bG9hZA==")
        viewModel.sendForAnalysis(
            imageBase64 = "cGF5bG9hZA==",
            squareCrop = false,
            clinicalContext = "   ",
        )
        viewModel.awaitResult()
        assertEquals(null, provider.lastRequest?.clinicalContext)
    }

    @Test
    fun `reset is idempotent and ignores dispatch from idle`() = runTest {
        val provider = FakeAiProvider(id = "fake-cloud")
        val viewModel = viewModel(AiOrchestrator(listOf(provider)))

        viewModel.reset()
        viewModel.sendForAnalysis(imageBase64 = "cGF5bG9hZA==", squareCrop = false)

        assertTrue(viewModel.uiState.value is ImageAnalysisUiState.Idle)
        assertEquals(0, provider.callCount)
    }
}
