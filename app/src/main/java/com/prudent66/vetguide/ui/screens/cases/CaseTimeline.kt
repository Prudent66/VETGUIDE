package com.prudent66.vetguide.ui.screens.cases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.model.Case
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Chronological clinical timeline with a before/after comparison entry. */
@Composable
internal fun TimelineContent(
    cases: List<com.prudent66.vetguide.domain.model.Case>,
    onCaseClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var compare by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            "Clinical Timeline",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (cases.size >= 2) {
            OutlinedButton(
                onClick = { compare = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("Compare Before / After") }
        }
        CaseList(
            cases = cases,
            onCaseClick = onCaseClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (compare) {
        AlertDialog(
            onDismissRequest = { compare = false },
            title = { Text("Before / After Comparison") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cases.take(2).forEachIndexed { index, case ->
                        val label = if (index == 0) "Before" else "After"
                        Text(
                            "$label — ${case.presentingComplaint} · " +
                                java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                    .format(java.util.Date(case.createdAtEpochMs)),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { compare = false }) { Text("Close") } },
        )
    }
}

