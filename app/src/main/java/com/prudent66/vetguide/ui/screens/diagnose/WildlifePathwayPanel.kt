package com.prudent66.vetguide.ui.screens.diagnose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.model.WildlifeDiseaseCatalog
import com.prudent66.vetguide.domain.model.WildlifeGroup

/** Wildlife group selector with linked diseases and biosafety cautions. */
@Composable
fun WildlifePathwayPanel(
    selectedGroup: WildlifeGroup = WildlifeGroup.WILD_RUMINANTS,
    onSelect: (WildlifeGroup) -> Unit = {},
) {
    val group = selectedGroup
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Wildlife group: ${group.label}", modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WildlifeGroup.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
        Text("Linked conditions & handling cautions", style = MaterialTheme.typography.titleSmall)
        WildlifeDiseaseCatalog.forGroup(group).forEach { disease ->
            Text("• ${disease.name}", style = MaterialTheme.typography.bodyMedium)
            Text(disease.caution, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
