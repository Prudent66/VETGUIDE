package com.prudent66.vetguide.ui.screens.knowledge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.KnowledgeFilters

@Composable
internal fun SpeciesFilter(
    category: AnimalCategory,
    selectedTag: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(category) { KnowledgeFilters.filterOptions(category) }
    Box(modifier = Modifier.padding(16.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Species: ${selectedTag ?: "All species"}")
            Icon(
                painter = painterResource(R.drawable.nav_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(if (option == "All species") null else option)
                        expanded = false
                    },
                )
            }
        }
    }
}

