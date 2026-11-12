package com.prudent66.vetguide.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.data.ai.ImageEncoder
import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.ai.AiRequest
import com.prudent66.vetguide.domain.ai.AiResult
import com.prudent66.vetguide.domain.model.Species
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 6 (§4.2) image-analysis state machine, kept separate from the
 * questionnaire flow so both ViewModels stay within their responsibility
 * (detekt TooManyFunctions). The species is handed over from the
 * questionnaire Start phase; Cancel abandons back to [Idle], which the
 * Diagnose screen renders as the questionnaire Start phase again.
 */
sealed interface ImageAnalysisUiState {

    /** Image flow not running — the Diagnose screen shows the Start phase. */
    data object Idle : ImageAnalysisUiState

    /** Live camera preview / gallery pick phase. */
    data class Capture(val species: Species?) : ImageAnalysisUiState

    /** Confirm/crop phase; [imageBase64] is already compressed JPEG (§4.2). */
    data class Review(val species: Species?, val imageBase64: String) : ImageAnalysisUiState

    /** §5 failover ladder in flight. */
    data class Analyzing(val species: Species?) : ImageAnalysisUiState

    /**
     * Outcome with mandatory provenance (§5.2/§10): a real provider id is an
     * "AI prediction"; [OFFLINE_PROVIDER_ID] is the deterministic rule-based
     * result, always visually distinct (§10).
     */
    data class Result(
        val species: Species?,
        val providerId: String,
        val narrative: String,
        val degraded: Boolean,
        /** Structured cloud analysis — null for the rule-based fallback. */
        val analysisData: AiAnalysisData? = null,
    ) : ImageAnalysisUiState

    /** Corrupt/unsupported image payload; retake restarts the capture phase. */
    data class ImageError(val species: Species?, val message: String) : ImageAnalysisUiState
}

@HiltViewModel
class ImageAnalysisViewModel @Inject constructor(
    private val orchestrator: AiOrchestrator,
) : ViewModel() {

    private val mut = MutableStateFlow<ImageAnalysisUiState>(ImageAnalysisUiState.Idle)
    val uiState: StateFlow<ImageAnalysisUiState> = mut.asStateFlow()

    /** Entry point from the questionnaire Start phase, carrying the species. */
    fun start(species: Species?) {
        mut.value = ImageAnalysisUiState.Capture(species)
    }

    /** Called with a compressed-JPEG Base64 payload after capture or pick. */
    fun onImageCaptured(imageBase64: String) {
        val capture = mut.value as? ImageAnalysisUiState.Capture ?: return
        mut.value = ImageAnalysisUiState.Review(
            species = capture.species,
            imageBase64 = imageBase64,
        )
    }

    /** Confirm/crop screen: back to the live preview. */
    fun retakeImage() {
        val review = mut.value as? ImageAnalysisUiState.Review ?: return
        mut.value = ImageAnalysisUiState.Capture(species = review.species)
    }

    /** Retake from the error state (corrupt payload). */
    fun retryFromError() {
        val error = mut.value as? ImageAnalysisUiState.ImageError ?: return
        mut.value = ImageAnalysisUiState.Capture(species = error.species)
    }

    /** Abandon the image flow — the questionnaire Start phase reappears. */
    fun reset() {
        mut.value = ImageAnalysisUiState.Idle
    }

    /**
     * Sends the image through the §5 failover ladder. The UI must already
     * have shown the §10 cloud-consent modal — consent to dispatch is a UI
     * gate in front of this call (per-artifact persistence lands with the
     * case-artifact work). Optional square crop runs off the main thread.
     */
    fun sendForAnalysis(imageBase64: String, squareCrop: Boolean) {
        val review = mut.value as? ImageAnalysisUiState.Review ?: return
        mut.value = ImageAnalysisUiState.Analyzing(species = review.species)
        viewModelScope.launch {
            try {
                val payloadBase64 = if (squareCrop) {
                    withContext(Dispatchers.IO) {
                        ImageEncoder.centerCropSquareToBase64(ImageEncoder.fromBase64(imageBase64))
                    }
                } else {
                    imageBase64
                }
                val result = orchestrator.analyze(imageRequest(review.species, payloadBase64))
                val success = result as? AiResult.ProviderSuccess
                mut.value = ImageAnalysisUiState.Result(
                    species = review.species,
                    providerId = result.providerId,
                    narrative = resultNarrative(result),
                    degraded = result is AiResult.ProviderSuccess && result.degraded,
                    analysisData = success?.analysisData,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                mut.value = ImageAnalysisUiState.ImageError(
                    species = review.species,
                    message = "Could not process the captured image: ${e.message}",
                )
            } catch (e: IllegalStateException) {
                mut.value = ImageAnalysisUiState.ImageError(
                    species = review.species,
                    message = "Image analysis unavailable: ${e.message}",
                )
            }
        }
    }

    private fun imageRequest(species: Species?, payloadBase64: String) = AiRequest(
        headline = species?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
            ?: "Unspecified species",
        findings = IMAGE_FINDINGS,
        imageBase64 = payloadBase64,
    )

    private fun resultNarrative(result: AiResult): String = when (result) {
        is AiResult.ProviderSuccess -> result.text
        is AiResult.DeterministicFallback -> result.text
    }

    private companion object {
        /** §4.2: findings text submitted with the image payload. */
        const val IMAGE_FINDINGS =
            "Photograph of the animal's presenting condition submitted for AI image analysis."
    }
}
