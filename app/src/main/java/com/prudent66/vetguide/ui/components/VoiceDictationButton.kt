package com.prudent66.vetguide.ui.components

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.R

/**
 * Hands-free dictation using the device's built-in speech service. Permission
 * is requested on first tap; devices without a speech recogniser get a quiet
 * explanatory line instead of a broken button.
 */
@Composable
fun VoiceDictationButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Dictate",
) {
    val context = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember(available) {
        if (available) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer?.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && recognizer != null) {
            recognizer.setRecognitionListener(
                DictationListener(onResult) { listening = false },
            )
            recognizer.startListening(dictationIntent())
            listening = true
        }
    }

    if (!available || recognizer == null) {
        Text(
            "Voice input is unavailable on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    OutlinedButton(
        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        modifier = modifier,
        enabled = !listening,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(if (listening) "Listening…" else label)
    }
}

private fun dictationIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
