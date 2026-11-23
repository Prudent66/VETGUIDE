package com.prudent66.vetguide.ui.components

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

/** Bridges the Android recognition callback into Compose state. */
internal class DictationListener(
    private val onResult: (String) -> Unit,
    private val onFinish: () -> Unit,
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = onFinish()
    override fun onError(error: Int) = onFinish()

    override fun onResults(results: Bundle?) {
        onFinish()
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.let(onResult)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
