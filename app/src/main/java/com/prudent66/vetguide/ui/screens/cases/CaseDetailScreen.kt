package com.prudent66.vetguide.ui.screens.cases

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.ui.viewmodels.CaseDetailUiState
import com.prudent66.vetguide.ui.viewmodels.CaseDetailViewModel
import com.prudent66.vetguide.ui.viewmodels.PdfState

/**
 * Phase 7 case record: patient info, saved image, AI findings, the editable
 * SOAP note, and PDF export to the Downloads folder. All edits persist to
 * Room; the PDF carries the standing clinical disclaimers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    onBack: () -> Unit,
    viewModel: CaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val pdfState by viewModel.pdfState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.exportPdf()
        } else {
            Toast.makeText(
                context,
                "Storage permission is needed to save the PDF to Downloads",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.soapSaved.collect {
            snackbarHostState.showSnackbar("SOAP note saved")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.saveError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(pdfState) {
        when (val pdf = pdfState) {
            is PdfState.Done -> Toast.makeText(
                context,
                "PDF saved to Downloads — ${pdf.fileName}",
                Toast.LENGTH_LONG,
            ).show()
            is PdfState.Failed -> Toast.makeText(context, pdf.message, Toast.LENGTH_LONG).show()
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Case record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.nav_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                CaseDetailUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                is CaseDetailUiState.Error -> Text(
                    text = s.message,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
                is CaseDetailUiState.Success -> CaseDetailContent(
                    state = s,
                    pdfWorking = pdfState == PdfState.Working,
                    viewModel = viewModel,
                    onExportPdf = {
                        startPdfExport(viewModel, context, storagePermissionLauncher::launch)
                    },
                )
            }
        }
    }
}

/** Runtime storage permission only applies to the legacy (API < 29) path. */
private fun startPdfExport(
    viewModel: CaseDetailViewModel,
    context: android.content.Context,
    requestPermission: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        viewModel.exportPdf()
    } else {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.exportPdf()
        } else {
            requestPermission(permission)
        }
    }
}
