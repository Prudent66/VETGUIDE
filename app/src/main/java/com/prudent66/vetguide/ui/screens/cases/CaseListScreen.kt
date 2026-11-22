package com.prudent66.vetguide.ui.screens.cases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.ui.viewmodels.CaseListUiState
import com.prudent66.vetguide.ui.viewmodels.CaseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Case timeline for one patient (Phase 1B + Phase 8 CRUD): complaint, status,
 * date. The top-bar menu edits or deletes the patient (cascade confirmation);
 * the FAB creates a demo case to verify the patient→case relation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseListScreen(
    onBack: () -> Unit,
    onCaseClick: (String) -> Unit = {},
    onEditPatient: (String) -> Unit = {},
    viewModel: CaseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CaseListTopBar(
                state = state,
                onBack = onBack,
                onEditPatient = onEditPatient,
                onDeletePatient = { confirmDelete = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    viewModel.createCase()
                    snackbarHostState.showSnackbar("Demo case created")
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.nav_add),
                    contentDescription = "Add demo case",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { padding ->
        CaseListBody(
            state = state,
            onCaseClick = onCaseClick,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }

    if (confirmDelete) {
        val patientName = (state as? CaseListUiState.Success)?.patientName ?: "this patient"
        DeletePatientDialog(
            patientName = patientName,
            onConfirm = {
                confirmDelete = false
                viewModel.deletePatient(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Loading/error/list body for the patient's case timeline. */
@Composable
private fun CaseListBody(
    state: CaseListUiState,
    onCaseClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (val s = state) {
            CaseListUiState.Loading -> LoadingBox()
            is CaseListUiState.Success ->
                if (s.cases.isEmpty()) {
                    EmptyState(s.patientName)
                } else {
                    CaseList(
                        cases = s.cases,
                        onCaseClick = onCaseClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            is CaseListUiState.Error -> ErrorBox(s.message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaseListTopBar(
    state: CaseListUiState,
    onBack: () -> Unit,
    onEditPatient: (String) -> Unit,
    onDeletePatient: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            val name = (state as? CaseListUiState.Success)?.patientName
            Text(if (name == null) "Cases" else "Cases · $name")
        },
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
                        contentDescription = "Patient actions",
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit patient") },
                        onClick = {
                            menuOpen = false
                            (state as? CaseListUiState.Success)?.let {
                                onEditPatient(it.patientId)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete patient") },
                        onClick = {
                            menuOpen = false
                            onDeletePatient()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun DeletePatientDialog(
    patientName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $patientName?") },
        text = { Text("Delete patient and all associated cases?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CaseList(
    cases: List<Case>,
    onCaseClick: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cases, key = { it.id }) { item ->
            CaseRow(item, onCaseClick = onCaseClick)
        }
    }
}

@Composable
private fun CaseRow(
    item: Case,
    onCaseClick: (String) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCaseClick(item.id) },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.presentingComplaint, style = MaterialTheme.typography.titleMedium)
            Text(
                text = item.status.label,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(item.status),
            )
            Text(
                text = dateFormat.format(Date(item.updatedAtEpochMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusColor(status: CaseStatus) = when (status) {
    CaseStatus.OPEN -> MaterialTheme.colorScheme.tertiary
    CaseStatus.INVESTIGATING -> MaterialTheme.colorScheme.primary
    CaseStatus.RECOVERED -> MaterialTheme.colorScheme.secondary
    CaseStatus.REFERRED -> MaterialTheme.colorScheme.secondary
    CaseStatus.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    CaseStatus.PLAN_SET -> MaterialTheme.colorScheme.secondary
    CaseStatus.RESOLVED -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun EmptyState(patientName: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No cases yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add the first case for $patientName with the + button",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
