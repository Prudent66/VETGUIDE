package com.prudent66.vetguide.ui.screens.diagnose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.prudent66.vetguide.data.ai.ImageEncoder
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 6 (§4.2) capture phase: live CameraX preview with a Capture button
 * and a gallery picker. Captured/picked frames are scaled down and
 * JPEG-compressed BEFORE Base64 — never the original full-res image to the
 * network (§4.2). Dispatch to the AI providers happens only after the §10
 * consent modal on the review screen.
 */
@Composable
fun ImageCaptureScreen(
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var captureError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            handleGalleryPick(context, uri, scope, onImageCaptured) { captureError = it }
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Photograph the presenting condition",
            style = MaterialTheme.typography.labelLarge,
        )
        if (hasCameraPermission) {
            CameraReadyContent(
                onImageCaptured = onImageCaptured,
                onError = { captureError = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            CameraRationale(
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
        CaptureControls(
            onGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            error = captureError,
            onCancel = onCancel,
        )
    }
}

/** Live preview + capture button (permission already granted). */
@Composable
private fun CameraReadyContent(
    onImageCaptured: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CameraPreviewArea(
                imageCapture = imageCapture,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Button(
            onClick = {
                captureImage(
                    context = context,
                    imageCapture = imageCapture,
                    scope = scope,
                    onImageCaptured = onImageCaptured,
                    onError = onError,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Capture") }
    }
}

@Composable
private fun CameraRationale(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Camera permission required", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "VETGUIDE needs the camera to photograph the animal's condition. " +
                    "You can still pick an existing photo from the gallery.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onGrant) { Text("Grant camera access") }
        }
    }
}

@Composable
private fun CaptureControls(
    onGallery: () -> Unit,
    error: String?,
    onCancel: () -> Unit,
) {
    OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
        Text("Pick from gallery")
    }
    error?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

/** Live back-camera preview bound to the current lifecycle owner. */
@Composable
private fun CameraPreviewArea(imageCapture: ImageCapture, modifier: Modifier) {
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = ContextCompat.getMainExecutor(context)
    val previewView = remember { PreviewView(context) }
    AndroidView(factory = { previewView }, modifier = modifier)
    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val binder = Runnable {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }
        providerFuture.addListener(binder, executor)
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
        }
    }
}

/** Gallery pick: bytes → compress → Base64, off the main thread (§4.2). */
private fun handleGalleryPick(
    context: Context,
    uri: Uri,
    scope: CoroutineScope,
    onImageCaptured: (String) -> Unit,
    onError: (String) -> Unit,
) {
    scope.launch {
        try {
            val rawBytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes()
                } ?: throw IOException("gallery stream unavailable")
            }
            val base64 = withContext(Dispatchers.Default) {
                ImageEncoder.compressToBase64(rawBytes)
            }
            onImageCaptured(base64)
        } catch (e: IOException) {
            onError("Could not read the selected image: ${e.message}")
        } catch (e: IllegalArgumentException) {
            onError("Unsupported image format: ${e.message}")
        }
    }
}

/** Capture: ImageProxy JPEG bytes → compress → Base64, off the main thread. */
private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    scope: CoroutineScope,
    onImageCaptured: (String) -> Unit,
    onError: (String) -> Unit,
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = image.planes.firstOrNull()?.buffer?.let { buffer ->
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                }
                image.close()
                if (bytes == null) {
                    onError("Camera returned an empty frame — try again")
                    return
                }
                scope.launch {
                    try {
                        val base64 = withContext(Dispatchers.Default) {
                            ImageEncoder.compressToBase64(bytes)
                        }
                        onImageCaptured(base64)
                    } catch (e: IllegalArgumentException) {
                        onError("Unsupported camera frame: ${e.message}")
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Capture failed: ${exception.message}")
            }
        },
    )
}
