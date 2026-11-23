package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dedicated prescription surface shared by the analysis results and the case
 * record. Drug/injection items with dosages live here, apart from the
 * non-pharmacological supportive-care steps rendered by the host screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionCard(
    prescriptions: List<String>,
    modifier: Modifier = Modifier,
) {
    if (prescriptions.isEmpty()) return
    var showSheet by rememberSaveable { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Prescriptions & Dosages", style = MaterialTheme.typography.titleMedium)
            Text(
                "Drugs, injections and dosages are kept separate from supportive field care. " +
                    "Verify every dose against the animal's species, weight and condition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Prescriptions & Dosages (${prescriptions.size})")
            }
        }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            PrescriptionSheetBody(prescriptions)
        }
    }
}

@Composable
private fun PrescriptionSheetBody(prescriptions: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Prescriptions & Dosages", style = MaterialTheme.typography.titleLarge)
        Text(
            "Decision support only — requires veterinary review. Confirm drug, dose, " +
                "route, frequency and duration for this species, weight and condition.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        prescriptions.forEachIndexed { index, item ->
            Text("${index + 1}. $item", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
