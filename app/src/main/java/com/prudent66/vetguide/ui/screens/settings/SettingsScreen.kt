package com.prudent66.vetguide.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.BuildConfig
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.ClinicProfile
import com.prudent66.vetguide.ui.viewmodels.CloudConnectionUiState
import com.prudent66.vetguide.ui.viewmodels.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings (Phase 4 + Phase 9 polish): clinic profile (existing app_settings
 * key/value table), cloud connectivity probe, JSON backup/restore, and the
 * About/clinical-disclaimer card. No schema change; backup URIs come from SAF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val busy by viewModel.backupBusy.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmImport by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClinicProfileCard(profile = profile, onSave = viewModel::saveProfile)
            ConnectivityCard(connection = connection, onTest = viewModel::testConnection)
            BackupRestoreCard(
                busy = busy,
                onExport = { exportLauncher.launch(backupFileName()) },
                onImport = { confirmImport = true },
            )
            AboutCard()
        }
    }

    if (confirmImport) {
        ImportBackupDialog(
            onConfirm = {
                confirmImport = false
                importLauncher.launch(importMimeTypes)
            },
            onDismiss = { confirmImport = false },
        )
    }
}

@Composable
private fun ClinicProfileCard(profile: ClinicProfile, onSave: (ClinicProfile) -> Unit) {
    var clinicName by rememberSaveable(profile.clinicName) { mutableStateOf(profile.clinicName) }
    var vetName by rememberSaveable(profile.vetName) { mutableStateOf(profile.vetName) }
    var contact by rememberSaveable(profile.contact) { mutableStateOf(profile.contact) }

    SettingsCard(title = "Clinic & Practitioner Profile") {
        Text(
            "Shown on the header and footer of exported SOAP PDFs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = clinicName,
            onValueChange = { clinicName = it },
            label = { Text("Clinic / practice name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vetName,
            onValueChange = { vetName = it },
            label = { Text("Veterinarian name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = contact,
            onValueChange = { contact = it },
            label = { Text("Contact phone / license ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onSave(ClinicProfile(clinicName = clinicName, vetName = vetName, contact = contact))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save profile") }
    }
}

@Composable
private fun ConnectivityCard(
    connection: CloudConnectionUiState,
    onTest: () -> Unit,
) {
    SettingsCard(title = "Cloud AI & Connectivity") {
        Text(
            "Cloud AI Analysis is always on",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "AI-assisted analysis is used automatically whenever connectivity allows. " +
                "When offline or slow, the app falls back to deterministic questionnaire " +
                "and offline knowledge-base results.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onTest,
            enabled = connection !is CloudConnectionUiState.Checking,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Test Cloud Connection") }
        ConnectionStatus(connection)
    }
}

@Composable
private fun ConnectionStatus(connection: CloudConnectionUiState) {
    when (connection) {
        CloudConnectionUiState.Idle -> Text(
            "Not tested yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CloudConnectionUiState.Checking -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Testing cloud connection…", style = MaterialTheme.typography.bodySmall)
        }
        is CloudConnectionUiState.Success -> {
            Text(
                "Connected · ${connection.elapsedMs} ms",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = "Backend status: ${connection.status}" +
                    (connection.mode?.let { " · mode: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is CloudConnectionUiState.Failure -> Text(
            "Failed after ${connection.elapsedMs} ms — ${connection.message}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BackupRestoreCard(busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    SettingsCard(title = "Data Backup & Restore") {
        Text(
            "Export patients, cases, and SOAP/AI notes as a JSON file, or restore one " +
                "after reinstalling or moving to a new device. Restoring is additive: " +
                "existing records with the same IDs are updated, never deleted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export Backup (JSON)") }
        OutlinedButton(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import Backup (JSON)") }
        if (busy) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Working…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AboutCard() {
    SettingsCard(title = "About & Clinical Disclaimer") {
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            "© 2026 Veloxdigitalforge - All Rights Reserved",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportBackupDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import backup?") },
        text = {
            Text(
                "Import patients, cases, and notes from the selected JSON backup? " +
                    "Existing records with the same IDs are updated; nothing is deleted.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Choose file") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun backupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "VETGUIDE-backup-$stamp.json"
}

private val importMimeTypes = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
)
