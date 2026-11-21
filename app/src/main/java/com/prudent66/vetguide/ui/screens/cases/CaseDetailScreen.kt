package com.prudent66.vetguide.ui.screens.cases

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.ui.viewmodels.CaseDetailUiState
import com.prudent66.vetguide.ui.viewmodels.CaseDetailViewModel
import com.prudent66.vetguide.ui.viewmodels.PdfState

/**
 * Phase 7 case record: patient info, saved image, AI findings, the editable
 * SOAP note, and PDF export to the Downloads folder. The top-bar menu edits
 * the case title/status or deletes the whole record (with confirmation).
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
    val onExportPdf = rememberExportAction(viewModel)
    var editOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    PdfToasts(pdfState)
    CaseSnackbarEffects(viewModel, snackbarHostState, onBack)

    Scaffold(
        topBar = {
            CaseDetailTopBar(
                onBack = onBack,
                onEdit = { editOpen = true },
                onDelete = { confirmDelete = true },
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
                    onExportPdf = onExportPdf,
                )
            }
        }
    }

    val success = state as? CaseDetailUiState.Success
    if (editOpen && success != null) {
        EditCaseDialog(
            case = success.case,
            onSave = { title, status ->
                editOpen = false
                viewModel.updateCaseDetails(presentingComplaint = title, status = status)
            },
            onDismiss = { editOpen = false },
        )
    }
    if (confirmDelete) {
        DeleteCaseDialog(
            onConfirm = {
                confirmDelete = false
                viewModel.deleteCase()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Top bar with the case-record actions menu (edit details / delete). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaseDetailTopBar(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.nav_more),
                        contentDescription = "Case actions",
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit case details") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete case") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun DeleteCaseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete case?") },
        text = { Text("Delete this case record?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Title/summary + five-state status editor (SOAP fields save on their own). */
@Composable
private fun EditCaseDialog(
    case: Case,
    onSave: (title: String, status: CaseStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(case.id) { mutableStateOf(case.presentingComplaint) }
    var status by remember(case.id) { mutableStateOf(CaseStatus.editable(case.status)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit case details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / summary") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Status", style = MaterialTheme.typography.labelLarge)
                CaseStatus.selectable.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { status = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = status == option,
                            onClick = { status = option },
                        )
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, status) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Export click handler: MediaStore path needs no permission; legacy does. */
@Composable
private fun rememberExportAction(viewModel: CaseDetailViewModel): () -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
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
    return remember(viewModel, permissionLauncher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                viewModel.exportPdf()
            } else {
                val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                val granted = ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    viewModel.exportPdf()
                } else {
                    permissionLauncher.launch(permission)
                }
            }
        }
    }
}

/** PDF export progress surfaced as toasts (§13: honest, visible outcomes). */
@Composable
private fun PdfToasts(pdfState: PdfState) {
    val context = LocalContext.current
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
}

@Composable
private fun CaseSnackbarEffects(
    viewModel: CaseDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onDeleted: () -> Unit,
) {
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
    LaunchedEffect(Unit) {
        viewModel.caseUpdated.collect {
            snackbarHostState.showSnackbar("Case details updated")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.caseDeleted.collect {
            onDeleted()
        }
    }
}
