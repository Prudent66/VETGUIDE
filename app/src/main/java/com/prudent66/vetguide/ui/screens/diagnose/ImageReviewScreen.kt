package com.prudent66.vetguide.ui.screens.diagnose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.data.ai.ImageEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 6 (§4.2) confirm/crop phase: preview the captured image, toggle a
 * simple square crop, then dispatch through the §10 consent modal. The modal
 * explains the cloud round-trip (secure backend → cloud AI provider) and is
 * the consent gate in front of [onSend] — the user can always cancel.
 */
@Composable
fun ImageReviewScreen(
    imageBase64: String,
    onSend: (squareCrop: Boolean) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    var squareCrop by rememberSaveable { mutableStateOf(true) }
    var showConsent by remember { mutableStateOf(false) }
    val preview by produceState<ImageBitmap?>(initialValue = null, imageBase64, squareCrop) {
        value = withContext(Dispatchers.Default) {
            decodePreview(imageBase64, squareCrop)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Confirm image", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (squareCrop) 1f else 4f / 3f),
        ) {
            val bitmap = preview
            if (bitmap == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Captured image preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "AI analysis is decision support — not a diagnosis. " +
                        "The veterinarian remains responsible for all clinical decisions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CropToggle(squareCrop = squareCrop, onToggle = { squareCrop = it })
        ReviewActions(
            onAnalyze = { showConsent = true },
            onRetake = onRetake,
            onCancel = onCancel,
        )
    }

    if (showConsent) {
        ConsentModal(
            onAllow = {
                showConsent = false
                onSend(squareCrop)
            },
            onDecline = { showConsent = false },
        )
    }
}

@Composable
private fun ReviewActions(onAnalyze: () -> Unit, onRetake: () -> Unit, onCancel: () -> Unit) {
    Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
        Text("Analyze with Cloud AI")
    }
    OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
        Text("Retake")
    }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

@Composable
private fun CropToggle(squareCrop: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Square crop", style = MaterialTheme.typography.titleSmall)
            Text(
                "Center-crop to a square before analysis — usually improves the focus " +
                    "on the affected area.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = squareCrop, onCheckedChange = onToggle)
    }
}

/** §10 consent modal: explains exactly where the image goes before dispatch. */
@Composable
private fun ConsentModal(onAllow: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Send image to Cloud AI?") },
        text = {
            Text(
                text = "The image will be compressed and sent through the secure " +
                    "VETGUIDE backend to a cloud AI provider. On-device mode keeps " +
                    "images local, and turning off \"Cloud AI Analysis\" in Settings " +
                    "disables cloud dispatch. Results are decision support only — " +
                    "the attending veterinarian remains responsible.",
            )
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow and analyze") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Cancel") } },
    )
}

/** Decode (and optionally square-crop) the stored payload for the preview. */
private fun decodePreview(imageBase64: String, squareCrop: Boolean): ImageBitmap? {
    val bytes = ImageEncoder.fromBase64(imageBase64)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val bitmap = if (squareCrop) {
        val side = minOf(decoded.width, decoded.height)
        Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
        )
    } else {
        decoded
    }
    return bitmap.asImageBitmap()
}
