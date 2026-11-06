package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.screens.knowledge.speciesLabel

/** Shared species selector covering the full Nigerian veterinary scope. */
@Composable
internal fun SpeciesField(
    selected: Species,
    onSelect: (Species) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = speciesLabel(selected), modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.nav_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Species.entries.forEach { species ->
            DropdownMenuItem(
                text = { Text(speciesLabel(species)) },
                onClick = {
                    onSelect(species)
                    expanded = false
                },
            )
        }
    }
}
