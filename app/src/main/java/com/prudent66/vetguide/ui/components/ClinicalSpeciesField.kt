package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.Species

/** Species selector that switches options with the Domestic / Wild category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalSpeciesField(
    category: AnimalCategory,
    selectedDomestic: Species,
    selectedWildlife: String?,
    onDomestic: (Species) -> Unit,
    onWildlife: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = remember(category) { ClinicalSpeciesCatalog.labels(category) }
    val value = if (category == AnimalCategory.WILD) {
        selectedWildlife?.takeIf { it in labels } ?: labels.first()
    } else {
        ClinicalSpeciesCatalog.label(selectedDomestic)
    }

    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.weight(1f)) { Text(value) }
        Icon(
            painter = painterResource(R.drawable.nav_chevron_down),
            contentDescription = null,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        labels.forEach { label ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    if (category == AnimalCategory.WILD) {
                        onWildlife(label)
                    } else {
                        ClinicalSpeciesCatalog.domesticSpecies(label)?.let(onDomestic)
                    }
                    expanded = false
                },
            )
        }
    }
}
